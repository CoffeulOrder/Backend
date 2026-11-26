#!/usr/bin/env python3
"""Drive only declared QA orders through existing APIs. Tokens remain in memory."""
import argparse
import json
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, HTTPRedirectHandler, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("API 주소가 리다이렉트되었습니다. 인증정보를 전송하지 않고 중단합니다.")


def request(base,path,body=None,token=None):
    headers={}
    if token:headers['Authorization']='Bearer '+token
    data=None if body is None else json.dumps(body).encode()
    if data is not None:headers['Content-Type']='application/json'
    try:
        with build_opener(NoRedirect()).open(Request(base+path,data=data,headers=headers),timeout=15) as r:return json.load(r)
    except HTTPError as error:
        try:code=json.load(error).get('code','HTTP_ERROR')
        except (ValueError,TypeError):code='HTTP_ERROR'
        finally:error.close()
        safeCode=code if isinstance(code,str) and code.replace('_','').isalnum() and len(code)<40 else 'HTTP_ERROR'
        raise RuntimeError(f'API 오류: HTTP {error.code} / {safeCode}') from None
    except (URLError,TimeoutError,OSError,ValueError):raise RuntimeError('개발 API 연결 또는 JSON 응답 확인에 실패했습니다.') from None


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['advance','confirm_fake'])
    parser.add_argument('--manifest',type=Path,required=True)
    parser.add_argument('--base-url',required=True)
    parser.add_argument('--order-id',type=int,required=True)
    parser.add_argument('--to-status',choices=['ACCEPTED','MAKING','READY','COMPLETED'])
    parser.add_argument('--confirm-isolated-target',action='store_true',help='격리 개발 서버임을 확인한 경우만 지정')
    parser.add_argument('--environment',required=True,choices=['dev','staging','test'])
    args=parser.parse_args()
    try:
        if not args.confirm_isolated_target:raise ValueError('공개 서버에 적용하지 마세요. --confirm-isolated-target 확인이 필요합니다.')
        parsed=urlparse(args.base_url)
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ['','/']:raise ValueError('기본 URL에는 자격증명·경로·쿼리를 넣지 마세요.')
        if parsed.scheme!='https' and not(parsed.scheme=='http' and parsed.hostname in ['localhost','127.0.0.1']):raise ValueError('HTTPS 또는 로컬 HTTP만 허용합니다.')
        manifest=json.loads(args.manifest.read_text())
        if manifest.get('version')!='ormo-android-qa-v1':raise ValueError('지원하는 QA manifest가 아닙니다.')
        order=next((o for o in manifest['orders'] if o['orderId']==args.order_id),None)
        if not order:raise ValueError('manifest에 선언한 QA 주문만 허용합니다.')
        account=next(a for a in manifest['accounts'] if a['alias']==order['account'])
        base=args.base_url.rstrip('/')
        customer=request(base,'/api/v1/auth/login',dict(email=account['email'],password=manifest['password']))['data']['accessToken']
        path='/api/v1/orders/'+str(args.order_id)
        if args.command=='confirm_fake':
            payment=request(base,path+'/payment',token=customer)['data']['payment']
            if payment['pgProvider']!='FAKE':raise ValueError('FAKE 주문만 확정할 수 있습니다. 실제 PG는 호출하지 않습니다.')
            result=request(base,'/api/v1/dev/orders/'+str(args.order_id)+'/confirm-payment',dict(pgOrderId=payment['pgOrderId']),customer)['data']
            if not result.get('simulated'):raise ValueError('개발용 모의 결제 응답이 아닙니다.')
            print(json.dumps({k:result.get(k) for k in ['orderId','status','pickupNo','simulated']},ensure_ascii=False));return 0
        if not args.to_status:raise ValueError('advance에는 --to-status가 필요합니다.')
        current=request(base,path,token=customer)['data']['status']
        states=['REQUESTED','ACCEPTED','MAKING','READY','COMPLETED'];actions=['accept','start','ready','complete']
        if current not in states or states.index(current)>states.index(args.to_status):raise ValueError('정방향 진행만 허용합니다. 결제 대기는 confirm_fake를 먼저 사용하세요.')
        owner=request(base,'/api/v1/auth/staff/login',dict(loginId='qa-owner-'+order['storeAlias'].lower(),password=manifest['password']))['data']['accessToken']
        for rank in range(states.index(current),states.index(args.to_status)):
            request(base,'/api/v1/staff/orders/'+str(args.order_id)+'/'+actions[rank],{},owner)
        print(json.dumps(dict(orderId=args.order_id,status=args.to_status),ensure_ascii=False));return 0
    except (ValueError,KeyError,StopIteration,RuntimeError,OSError) as error:
        print('QA_API_ERROR: '+str(error),file=sys.stderr);return 1


if __name__=='__main__':sys.exit(main())
