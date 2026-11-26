#!/usr/bin/env python3
"""ORMO Android QA fixture compiler. Never reads application settings or secrets."""
from __future__ import annotations
import argparse
import calendar
import csv
import hashlib
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

KST = ZoneInfo('Asia/Seoul')
UTC = timezone.utc
BASE = 8_510_000_000
VERSION = 'ormo-android-qa-v1'
PASSWORD = 'Qa123456!'  # Deliberately public, synthetic QA password. Never a real credential.
PASSWORD_HASH = '$2a$10$VlBhrlMnORAUOm2bbHwqAuQBh.K8UEDjCzqN1mlyALqPRhMSUelgK'
STATES = ['PENDING_PAYMENT','REQUESTED','ACCEPTED','MAKING','READY','COMPLETED','CANCELED','REJECTED','EXPIRED']
FORWARD = ['REQUESTED','ACCEPTED','MAKING','READY','COMPLETED']
COUNTS = {1:48,2:20,3:12,4:12,5:15,6:10,7:8,8:3,9:6,10:5,11:0,12:10,13:8}
SCHOOL_A = '__SCHOOL_A_SQL__'
IMAGE_BASE = 'https://picsum.photos'


def parseTime(value=None):
    if value is None:
        return datetime.now(KST).replace(microsecond=0)
    parsed = datetime.fromisoformat(value.replace('Z','+00:00'))
    if parsed.tzinfo is None:
        raise ValueError('시각에는 +09:00 또는 Z 등 오프셋이 필요합니다.')
    return parsed.astimezone(KST).replace(microsecond=0)


def sqlValue(value):
    if value == SCHOOL_A:
        return '@qaSchoolA'
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return '1' if value else '0'
    if isinstance(value, int):
        return str(value)
    if isinstance(value, datetime):
        value = value.astimezone(UTC).strftime('%Y-%m-%d %H:%M:%S.%f')
    # Hex UTF-8 avoids SQL-mode dependent escaping of quotes, newlines, backslashes and emoji.
    return "CONVERT(X'%s' USING utf8mb4)" % str(value).encode('utf-8').hex()


def upsert(table, row, keys=('id',)):
    cols = ','.join('`'+key+'`' for key in row)
    values = ','.join(sqlValue(value) for value in row.values())
    updates = ','.join('`%s`=VALUES(`%s`)' % (key,key) for key in row if key not in keys)
    return f'INSERT INTO `{table}` ({cols}) VALUES ({values}) ON DUPLICATE KEY UPDATE {updates};'


@dataclass
class Fixture:
    table: str
    alias: str
    row: dict


class Catalog:
    def __init__(self, baseTime, sessionHours=8):
        self.baseTime = baseTime
        self.sessionHours = sessionHours
        self.rows = []
        self.byAlias = {}
        self.accounts = []
        self.orders = []
        self.options = []
        self.storeScenarios = []
        self.beforeReorder = {}
        self.reorderCases = []
        self.historyPickups = defaultdict(lambda: 1999)
        self.signupEmails = [f'qa-signup{i:02}@g.eulji.ac.kr' for i in range(1,21)]
        self.verificationEmails = ['qa-ev-valid@g.eulji.ac.kr','qa-ev-wrong@g.eulji.ac.kr','qa-ev-expired@g.eulji.ac.kr','qa-ev-resend@g.eulji.ac.kr','qa-ev-daily@g.eulji.ac.kr']
        self.serial = Counter()
        self.pickups = defaultdict(int)
        self.build()

    def idFor(self, kind, index):
        offsets = {'school':100,'merchant':1000,'store':2000,'staff':3000,'member':4000,'category':10000,'menu':20000,'group':100000,'option':200000,'order':300000,'line':400000,'line-option':500000,'payment':600000,'refund':700000,'history':800000,'terms':900000,'verification':950000,'pg':960000}
        return BASE + offsets[kind] + index

    def add(self, table, alias, row):
        if alias in self.byAlias:
            raise ValueError('중복 별칭: '+alias)
        fixture = Fixture(table, alias, row)
        self.rows.append(fixture)
        self.byAlias[alias] = fixture
        return row.get('id')

    def member(self, alias, email, school=SCHOOL_A, status='ACTIVE', consumable=False, locked=False, displayName=None, fixedIndex=None):
        memberId = self.idFor('member',fixedIndex or len(self.accounts)+1)
        row = dict(id=memberId,school_id=school,email=email,password_hash=PASSWORD_HASH,name=displayName or 'QA '+alias,status=status,failed_login_count=5 if locked else 0,locked_until=self.baseTime+timedelta(minutes=35) if locked else None,last_login_at=None,withdrawn_at=None,created_at=self.baseTime-timedelta(days=30),updated_at=self.baseTime)
        if status=='WITHDRAWN':
            row.update(email=None,password_hash=None,name='탈퇴회원',withdrawn_at=self.baseTime-timedelta(days=1))
        self.add('member',alias,row)
        self.accounts.append(dict(alias=alias,memberId=memberId,email=email,school='A' if school==SCHOOL_A else 'B' if school==self.idFor('school',2) else 'C',status=status,consumable=consumable,locked=locked,name=row['name']))
        for typ in ['SERVICE','PRIVACY']:
            self.serial['terms']+=1
            self.add('terms_agreement',alias+'-'+typ,dict(id=self.idFor('terms',self.serial['terms']),member_id=memberId,terms_type=typ,terms_version='2026-11-25',agreed_at=self.baseTime-timedelta(days=30)))
        return memberId

    def build(self):
        now=self.baseTime
        for idx in [2,3]:
            school=self.idFor('school',idx)
            self.add('school','school-'+chr(64+idx),dict(id=school,name='QA 가상대학교 '+chr(64+idx),campus='가상캠퍼스',status='ACTIVE',created_at=now,updated_at=now))
            self.add('school_email_domain','domain-'+chr(64+idx),dict(id=self.idFor('school',idx+10),school_id=school,domain=f'qa-{chr(96+idx)}.example',created_at=now))
        statuses={1:'OPEN',2:'PAUSED',3:'PAUSED',4:'PAUSED',5:'CLOSED',6:'CLOSED',7:'CLOSED'}
        for storeIndex,count in COUNTS.items():
            alias=f'S{storeIndex:02}'
            merchant=self.idFor('merchant',storeIndex)
            store=self.idFor('store',storeIndex)
            school=SCHOOL_A if storeIndex<=11 else self.idFor('school',storeIndex-10)
            address=('가상주소 ' + '아주 긴 QA 주소입니다. '*15)[:250] if storeIndex==5 else '가상시 가상구 QA로 '+str(storeIndex)
            self.add('merchant',alias+'-seller',dict(id=merchant,business_name='QA 가상사업자 '+alias,business_reg_no=f'000000{storeIndex:04}',representative_name='가상대표 '+alias,business_address=address,contact_phone='000-0000-0000',mail_order_reg_no=None if storeIndex==2 else 'QA-가상신고-'+alias,commission_rate='0.0000',status='ACTIVE',created_at=now,updated_at=now))
            self.add('store',alias,dict(id=store,merchant_id=merchant,school_id=school,name='QA '+alias+' 가상매장',location='QA 캠퍼스 인근 위치 예시 '+alias,status=statuses.get(storeIndex,'OPEN'),notice='QA 전용 가상 데이터입니다. 실제 판매 매장이 아닙니다.',created_at=now-timedelta(days=60),updated_at=now))
            self.add('merchant_pg',alias+'-fake',dict(id=self.idFor('pg',storeIndex),merchant_id=merchant,pg_provider='FAKE',mid=None,client_key='qa-fake-no-real-key',secret_ref='fake://ormo-qa/no-secret',status='ACTIVE',portone_store_id=None,created_at=now,updated_at=now))
            owner=self.idFor('staff',storeIndex)
            self.add('staff_account',alias+'-owner',dict(id=owner,merchant_id=merchant,role='OWNER',login_id=f'qa-owner-{alias.lower()}',password_hash=PASSWORD_HASH,name='QA 사장님 '+alias,status='ACTIVE',failed_login_count=0,locked_until=None,last_login_at=None,created_at=now,updated_at=now))
            for day in range(1,8):
                closed=(storeIndex==1 and day==7) or (storeIndex==5 and day not in [1,3,5]) or storeIndex==7
                opening='08:30:00' if storeIndex==1 and day<=5 else '10:00:00'
                closing='18:00:00' if storeIndex==1 and day<=5 else '15:00:00' if storeIndex==1 else '21:00:00'
                if storeIndex==10: opening,closing='22:00:00','23:59:59'  # Explicitly documented fallback, not overnight support.
                if storeIndex==8: closed,opening,closing=True,'10:00:00',None
                self.add('store_business_hour',alias+f'-hour-{day}',dict(store_id=store,day_of_week=day,open_time=opening if not closed or storeIndex==8 else None,close_time=closing if not closed else None,is_closed=closed))
            reopen=now+timedelta(minutes=35) if storeIndex in [2,5] else (now+timedelta(days=1)).replace(hour=8,minute=30,second=0) if storeIndex in [3,6] else None
            self.storeScenarios.append(dict(alias=alias,storeId=store,status=statuses.get(storeIndex,'OPEN'),requestedReopenAt=reopen.isoformat() if reopen else None,reopenSupported=False,menuCount=count,school='A' if storeIndex<=11 else chr(64+storeIndex-10),latitude=None,longitude=None,coordinateSupported=False))
            categories=[('커피',18),('티·음료',10),('디저트',8),('베이커리',6),('지금만 만나는 계절 한정 특별 메뉴',4),('',2),('빈 카테고리',0)] if storeIndex==1 else [('기본 메뉴',count)]
            menuIndex=0
            for categoryIndex,(name,menuCount) in enumerate(categories,1):
                categoryId=self.idFor('category',storeIndex*100+categoryIndex)
                self.add('category',alias+f'-C{categoryIndex}',dict(id=categoryId,store_id=store,name=name,sort_order=categoryIndex,is_visible=True,created_at=now,updated_at=now))
                for _ in range(menuCount):
                    menuIndex+=1
                    menuAlias=f'M{storeIndex:02}{menuIndex:02}'
                    menuId=self.idFor('menu',storeIndex*100+menuIndex)
                    baseNames=['아메리카노','카페라떼','아이스티','초코라떼','히비스커스 티']
                    price=[1500,2500,2000,3000,3000][(menuIndex-1)%5]
                    if menuIndex==12: price=500
                    if menuIndex==13: price=3500
                    if menuIndex==14: price=12000
                    if menuIndex==15: price=100000
                    menuName=baseNames[(menuIndex-1)%5]+f' QA {menuAlias}'
                    if menuIndex==16: menuName='아주 긴 이름을 가진 시즌 특별 프리미엄 음료 메뉴입니다 '+menuAlias
                    imageKey=f'seed/ormo-qa-{(menuIndex-1)%30+1:02}/480/360.jpg'
                    if storeIndex==1 and 43<=menuIndex<=46: imageKey=None
                    if storeIndex==1 and menuIndex>=47: imageKey=f'ormo-qa-missing-{menuIndex}'
                    age=0 if menuIndex in [12,37] else 13 if menuIndex in [13,38] else 14 if menuIndex==14 else 30
                    self.add('menu_item',menuAlias,dict(id=menuId,store_id=store,name=menuName,description='QA 가상 메뉴 설명. 실제 판매 상품이 아닙니다.',base_price=price,image_key=imageKey,is_sold_out=(storeIndex==9 or storeIndex==1 and 37<=menuIndex<=42),status='ACTIVE',is_new=age<14,is_event=menuIndex in [5,9,12,37,39],sort_order=menuIndex,version=0,created_at=now-timedelta(days=age),updated_at=now))
                    self.add('menu_item_category',menuAlias+'-category',dict(menu_item_id=menuId,category_id=categoryId,sort_order=menuIndex))
            if storeIndex==1: self.buildOptions()
            if storeIndex==3:
                for mi in range(1,11):
                    self.addHistoryOptions(self.byAlias[f'M03{mi:02}'].row['id'], mi)
        # Assigned STAFF is separate from OWNER; another store must be forbidden.
        self.add('staff_account','S01-staff',dict(id=self.idFor('staff',100),merchant_id=self.idFor('merchant',1),role='STAFF',login_id='qa-staff-s01',password_hash=PASSWORD_HASH,name='QA 배정직원',status='ACTIVE',failed_login_count=0,locked_until=None,created_at=now,updated_at=now))
        self.add('staff_store','S01-staff-link',dict(staff_account_id=self.idFor('staff',100),store_id=self.idFor('store',1),created_at=now))
        for idx in range(1,6): self.member(f'qa{idx:02}',f'qa{idx:02}@g.eulji.ac.kr')
        for alias in ['qa-heavy','qa-page20','qa-page21','qa-filtered','qa-live1','qa-live3','qa-live5','qa-wd-block','qa-states','qa-payments','qa-popular']:
            self.member(alias,alias+'@g.eulji.ac.kr')
        for idx in range(1,6): self.member(f'qa-wd{idx:02}',f'qa-wd{idx:02}@g.eulji.ac.kr',consumable=True)
        for idx in range(1,6): self.member(f'qa-pw{idx:02}',f'qa-pw{idx:02}@g.eulji.ac.kr',consumable=True)
        for idx in range(1,6): self.member(f'qa-reset{idx:02}',f'qa-reset{idx:02}@g.eulji.ac.kr',consumable=True)
        self.member('qa-suspended','qa-suspended@g.eulji.ac.kr',status='SUSPENDED')
        self.member('qa-locked','qa-locked@g.eulji.ac.kr',locked=True)
        self.member('qa-withdrawn','qa-withdrawn@g.eulji.ac.kr',status='WITHDRAWN')
        self.member('qa-school-b','qa01@qa-b.example',school=self.idFor('school',2))
        self.member('qa-school-c','qa01@qa-c.example',school=self.idFor('school',3))
        corePool=[f'M01{i:02}' for i in range(1,22) if i!=8]
        for index in range(150): self.order('qa-heavy',1 if index<80 else 2,['COMPLETED','CANCELED','REJECTED'][index%3],label=f'heavy-{index+1:03}',ageMinutes=60+index,variant=index%4 if index<80 else 0,menuPool=corePool if index<80 else None,pickupBand='history' if index<80 else None,currentSnapshot=index<80)
        for count in [20,21]:
            for index in range(count): self.order(f'qa-page{count}',1,'COMPLETED',label=f'page{count}-{index+1:02}',ageMinutes=120+index,pickupBand='history',currentSnapshot=True)
        for index in range(30):
            self.order('qa-filtered',1,'EXPIRED' if index<25 else 'COMPLETED',label=f'filtered-{index+1:02}',ageMinutes=30+index,pickupBand='history',currentSnapshot=True)
        self.order('qa-live1',1,'REQUESTED',label='live1-1',ageMinutes=8)
        for index,status in enumerate(['REQUESTED','ACCEPTED','MAKING']): self.order('qa-live3',1,status,label=f'live3-{index+1}',ageMinutes=7-index)
        for index,status in enumerate(['REQUESTED','ACCEPTED','MAKING','READY','READY']): self.order('qa-live5',1,status,label=f'live5-{index+1}',ageMinutes=4-index/10)
        self.order('qa-wd-block',1,'REQUESTED',label='withdraw-block',ageMinutes=5)
        for status in STATES:
            for variant in range(4):
                self.order('qa-states',1 if status=='PENDING_PAYMENT' else 3,status,label=f'{status.lower()}-{variant+1}',variant=variant,ageMinutes=3+variant)
        for day in range(7): self.order('qa-states',3,'COMPLETED',label=f'weekday-{day}',when=(now-timedelta(days=day+1)).replace(hour=12,minute=0,second=0))
        month=now.month-1 or 12; year=now.year-(now.month==1)
        previousMonth=now.replace(year=year,month=month,day=min(now.day,calendar.monthrange(year,month)[1]))
        for label,when in [('calendar-month-ago',previousMonth),('calendar-last-dec31',now.replace(year=now.year-1,month=12,day=31,hour=23,minute=59,second=0)),('calendar-jan1',now.replace(month=1,day=1,hour=0,minute=0,second=0))]: self.order('qa-states',3,'COMPLETED',label=label,when=when)
        self.order('qa-payments',3,'COMPLETED',label='four-fail-then-paid',attempts=5,ageMinutes=90)
        self.order('qa-payments',1,'PENDING_PAYMENT',label='five-failures',attempts=5,allFailed=True)
        self.order('qa-payments',1,'PENDING_PAYMENT',label='payment-unknown',unknown=True)
        self.order('qa-payments',3,'EXPIRED',label='expired-system-refund',systemRefund=True,refundStatus='SUCCEEDED',ageMinutes=90)
        self.order('qa-payments',3,'CANCELED',label='refund-requested',refundStatus='REQUESTED',ageMinutes=90)
        self.order('qa-payments',3,'REJECTED',label='refund-failed',refundStatus='FAILED',ageMinutes=90)
        for index in range(5): self.order('qa-popular',1,'COMPLETED',label=f'popular-{index+1}',menuIndex=index+1,popular=True,ageMinutes=60*(index+1))
        for idx in range(1,6): self.addVerification(f'qa-reset{idx:02}@g.eulji.ac.kr','PASSWORD_RESET','valid',idx)
        for idx,scenario in enumerate(['valid','wrong','expired','resend','daily'],20):
            self.addVerification('qa-ev-'+scenario+'@g.eulji.ac.kr','SIGNUP',scenario,idx)
        for idx,email in enumerate(self.signupEmails,50): self.addVerification(email,'SIGNUP','valid',idx)
        self.buildReorderCases()
        self.validate()

    def buildReorderCases(self):
        # Append IDs after the original 36 accounts / 288 orders. Avoid normal runtime AUTO_INCREMENT values.
        self.serial['terms']=1000
        self.member('qa-reorder','qa-reorder@g.eulji.ac.kr',fixedIndex=1001)
        self.member('qa-multistore','qa-multistore@g.eulji.ac.kr',fixedIndex=1002)
        self.member('qa-longname-ko','qa-longname-ko@g.eulji.ac.kr',displayName='가나다라마바사아자차카타파하가나다라마바',fixedIndex=1003)
        self.member('qa-longname-en','qa-longname-en@g.eulji.ac.kr',displayName='ABCDEFGHIJKLMNOPQRST',fixedIndex=1004)
        assert len(self.byAlias['qa-longname-ko'].row['name'])==20
        self.serial['order']=2000
        self.serial['payment']=2000
        self.serial['history']=4000
        # Dedicated menu 30 has one available replacement option after its original option becomes sold out.
        self.serial['group']+=1;gid=self.idFor('group',self.serial['group'])
        menu=self.byAlias['M0130'].row['id']
        self.add('option_group','R5-group',dict(id=gid,menu_item_id=menu,name='재주문 전용 선택',option_type='EXTRA',is_required=True,min_select=1,max_select=1,sort_order=1,created_at=self.baseTime,updated_at=self.baseTime))
        for i in range(2):
            self.serial['option']+=1
            self.add('option_item',f'R5-option-{i+1}',dict(id=self.idFor('option',self.serial['option']),option_group_id=gid,name='주문 당시 옵션' if i==0 else '대체 가능 옵션',price_delta=0 if i==0 else 500,is_default=i==0,is_sold_out=False,sort_order=i,created_at=self.baseTime,updated_at=self.baseTime))
        core=[f'M01{i:02}' for i in range(1,22) if i!=8]
        specs=[('R1',1,['M0122','M0123'],'주문 메뉴 전부 품절'),('R2',1,['M0124','M0125','M0126'],'3줄 중 1줄만 품절'),('R3',1,['M0127','M0128'],'한 메뉴 HIDDEN; FK 보존'),('R4',1,['M0129'],'주문 뒤 기본가 +1000원'),('R5',1,['M0130'],'선택했던 옵션 품절; 대체 옵션 정상'),('R6',2,['M0201'],'준비 중인 S02'),('R7',1,core,'20줄; 첫 줄 수량20 + 기존 장바구니1'),('R8',8,['M0801'],'다른 OPEN 매장 S08')]
        for alias,store,menus,description in specs:
            lines=[dict(menuAlias=m,quantity=20 if alias=='R7' and i==0 else 1) for i,m in enumerate(menus)]
            self.order('qa-reorder',store,'COMPLETED',label=alias,lineSpecs=lines,currentSnapshot=True,pickupBand='history',ageMinutes=180)
            info=dict(self.orders[-1]);info.update(description=description)
            if alias=='R7':info['cartPrecondition']=dict(menuId=self.byAlias['M0101'].row['id'],quantity=1,mergedQuantity=21)
            self.reorderCases.append(info)
        for store,count in [(1,2),(8,2),(2,1)]:
            for i in range(count):self.order('qa-multistore',store,'COMPLETED',label=f'multistore-s{store:02}-{i+1}',ageMinutes=120+i,currentSnapshot=True,pickupBand='history')
        # Record old values first. SQL inserts orders between these baseline rows and the mutations.
        for alias,changes in [('M0122',dict(is_sold_out=True)),('M0123',dict(is_sold_out=True)),('M0124',dict(is_sold_out=True)),('M0127',dict(status='HIDDEN')),('M0129',dict(base_price=self.byAlias['M0129'].row['base_price']+1000)),('R5-option-1',dict(is_sold_out=True))]:
            fixture=self.byAlias[alias]
            self.beforeReorder[(fixture.table,fixture.row['id'])]=dict(fixture.row)
            fixture.row.update(changes)

    def buildOptions(self):
        # Each numbered case belongs to the correspondingly numbered S01 menu.
        cases=[('O1','옵션 없음',[]),('O2','필수 단일 온도',[(True,1,1,'TEMPERATURE',['HOT','ICE'],False)]),('O3','선택 단일',[(False,0,1,'EXTRA',['샷 추가','시럽 추가'],False)]),('O4','필수 복수 1~2',[(True,1,2,'EXTRA',['샷 추가','시럽 추가','휘핑'],False)]),('O5','선택 복수 최대 3',[(False,0,3,'EXTRA',['샷 추가','시럽 추가','휘핑','얼음 적게'],False)]),('O6','선택 복수 제한 없음 요청 → 전체 6개 상한',[(False,0,6,'EXTRA',[f'추가 선택 {i}' for i in range(1,7)],False)]),('O7','필수 단일 일부 품절',[(True,1,1,'SIZE',['R','L'],False)]),('O8','필수 그룹 전부 품절',[(True,1,1,'TEMPERATURE',['HOT','ICE'],True)]),('O9','선택 그룹 전부 품절',[(False,0,1,'EXTRA',['휘핑','시럽'],True)]),('O10','복수 그룹·긴 옵션',[(True,1,1,'TEMPERATURE',['HOT','ICE'],False),(True,1,1,'SIZE',['R','L'],False),(False,0,6,'EXTRA',[f'긴 추가 옵션 {i}' for i in range(1,7)],False)]),('O11','선택 단일 중복 기본값 방어',[(False,0,1,'EXTRA',['기본값 A','기본값 B'],False)])]
        for index,(alias,title,groups) in enumerate(cases,1):
            menu=self.byAlias[f'M01{index:02}'].row['id']
            info=dict(alias=alias,menuAlias=f'M01{index:02}',menuId=menu,title=title,groupIds=[],optionItemIds=[])
            for groupIndex,(required,minSelect,maxSelect,typ,names,allSold) in enumerate(groups,1):
                self.serial['group']+=1
                gid=self.idFor('group',self.serial['group'])
                groupName={'TEMPERATURE':'온도','SIZE':'사이즈','EXTRA':'추가 선택'}[typ]
                self.add('option_group',alias+f'-G{groupIndex}',dict(id=gid,menu_item_id=menu,name=groupName,option_type=typ,is_required=required,min_select=minSelect,max_select=maxSelect,sort_order=groupIndex,created_at=self.baseTime,updated_at=self.baseTime))
                info['groupIds'].append(gid)
                for optionIndex,name in enumerate(names):
                    self.serial['option']+=1
                    oid=self.idFor('option',self.serial['option'])
                    self.add('option_item',alias+f'-G{groupIndex}-I{optionIndex+1}',dict(id=oid,option_group_id=gid,name=name,price_delta=0 if optionIndex==0 else 500*(optionIndex%3+1),is_default=optionIndex==0 or alias=='O11',is_sold_out=allSold or alias=='O7' and optionIndex==0,sort_order=optionIndex,created_at=self.baseTime,updated_at=self.baseTime))
                    info['optionItemIds'].append(oid)
            self.options.append(info)

    def addHistoryOptions(self,menuId,menuIndex):
        self.serial['group']+=1
        gid=self.idFor('group',self.serial['group'])
        self.add('option_group',f'HISTORY-M{menuIndex}-G',dict(id=gid,menu_item_id=menuId,name='QA 추가 선택',option_type='EXTRA',is_required=False,min_select=0,max_select=6,sort_order=1,created_at=self.baseTime,updated_at=self.baseTime))
        for i,name in enumerate(['ICE','샷 추가','시럽 추가','휘핑 추가','얼음 적게','진하게']):
            self.serial['option']+=1
            self.add('option_item',f'HISTORY-M{menuIndex}-I{i}',dict(id=self.idFor('option',self.serial['option']),option_group_id=gid,name=name,price_delta=500,is_default=False,is_sold_out=False,sort_order=i,created_at=self.baseTime,updated_at=self.baseTime))

    def addVerification(self,email,purpose,scenario,offset):
        count=10 if scenario=='daily' else 1
        for i in range(count):
            now=self.baseTime
            created=(now.replace(hour=0,minute=0,second=0)+timedelta(microseconds=i)) if scenario=='daily' else now
            expires=now-timedelta(seconds=1) if scenario=='expired' else now+timedelta(minutes=5)
            self.add('email_verification',f'EV-{offset}-{i}',dict(id=self.idFor('verification',offset*20+i),email=email,purpose=purpose,code_hash=hashlib.sha256(b'123456').hexdigest(),expires_at=expires,attempt_count=0,verified_at=None,token_hash=None,token_expires_at=None,consumed_at=None,created_at=created))

    def order(self,account,storeIndex,status,label,variant=0,ageMinutes=2,attempts=1,allFailed=False,unknown=False,systemRefund=False,refundStatus=None,menuIndex=1,popular=False,when=None,menuPool=None,lineSpecs=None,pickupBand=None,currentSnapshot=False):
        self.serial['order']+=1
        n=self.serial['order'];oid=self.idFor('order',n)
        member=self.byAlias[account].row['id'];store=self.idFor('store',storeIndex)
        created=self.baseTime-timedelta(minutes=ageMinutes)
        if variant==1: created=self.baseTime-timedelta(days=1,minutes=60)
        if variant==2: created=self.baseTime-timedelta(days=7,minutes=60)
        if variant==3: created=self.baseTime.replace(month=1,day=1,hour=0,minute=0,second=0)
        if when is not None: created=when
        if status=='EXPIRED' and created+timedelta(minutes=20)>self.baseTime: created=self.baseTime-timedelta(minutes=30)
        if status=='PENDING_PAYMENT': created=self.baseTime-timedelta(minutes=2)
        expired=status=='EXPIRED'
        placed=None if status in ['PENDING_PAYMENT','EXPIRED'] else created+timedelta(seconds=10)
        if placed:
            dateKey=placed.date().isoformat();key=(store,dateKey)
            pickupSource=self.historyPickups if pickupBand=='history' else self.pickups
            number=pickupSource[key]+1
            if label=='live1-1': number=max(number,1)
            if label=='live3-1': number=max(number,27)
            if label=='live5-1': number=max(number,100)
            if label=='completed-1': number=max(number,999)
            pickupSource[key]=number
        else: dateKey=None;number=None
        memo=[None,'얼음 적게 부탁해요.','가'*100,'잘 부탁해요 ☕ 😀\n픽업하러 갈게요.'][variant]
        row=dict(id=oid,order_code=f'QA-{n:06}',member_id=member,store_id=store,school_id=SCHOOL_A,status=status,business_date=dateKey,pickup_no=number,subtotal_amount=0,discount_amount=0,total_amount=0,request_memo=memo,reject_reason_code='SOLD_OUT' if status=='REJECTED' else None,reject_reason_detail='QA 가상 품절' if status=='REJECTED' else None,withdrawal_limit_agreed_at=created,commission_rate_snapshot='0.0000',idempotency_key='qa-seed-'+label,request_hash=hashlib.sha256(label.encode()).hexdigest(),expires_at=self.baseTime+timedelta(hours=self.sessionHours) if status=='PENDING_PAYMENT' else created+timedelta(minutes=20),placed_at=placed,accepted_at=None,making_at=None,ready_at=None,completed_at=None,canceled_at=None,rejected_at=None,expired_at=created+timedelta(minutes=20) if expired else None,version=0,created_at=created,updated_at=self.baseTime)
        rank=FORWARD.index(status) if status in FORWARD else -1
        for ix,(step,column) in enumerate([('ACCEPTED','accepted_at'),('MAKING','making_at'),('READY','ready_at'),('COMPLETED','completed_at')],1):
            if rank>=ix: row[column]=created+timedelta(seconds=10+ix*10)
        if status=='CANCELED': row['canceled_at']=created+timedelta(minutes=1)
        if status=='REJECTED': row['rejected_at']=created+timedelta(minutes=1)
        if variant==3 and status in ['MAKING','READY','COMPLETED']: row['accepted_at']=None
        self.add('orders',label,row)
        lineCount=len(lineSpecs) if lineSpecs else [1,2,10,20][variant]
        reorderItems=[]
        lineIds=[]
        for index in range(lineCount):
            self.serial['line']+=1;lid=self.idFor('line',self.serial['line']);lineIds.append(lid)
            menuAlias=lineSpecs[index]['menuAlias'] if lineSpecs else menuPool[index%len(menuPool)] if menuPool else f'M{storeIndex:02}{(menuIndex-1+index)%max(COUNTS[storeIndex],1)+1:02}'
            source=self.byAlias[menuAlias].row
            quantity=lineSpecs[index]['quantity'] if lineSpecs else 20 if variant==2 else (index+1 if popular else 2 if variant==1 else 1)
            price=500 if variant==0 and n%4==0 else 3500 if variant==1 else 10000 if variant==2 else source['base_price']
            if currentSnapshot:price=source['base_price']
            wanted=0 if variant==0 or variant==3 else 2 if variant==1 else 6
            picked=[]
            for group in [f.row for f in self.rows if f.table=='option_group' and f.row['menu_item_id']==source['id']]:
                options=[f.row for f in self.rows if f.table=='option_item' and f.row['option_group_id']==group['id']]
                if currentSnapshot:options=[o for o in options if not o['is_sold_out']]
                options.sort(key=lambda o:(o['is_sold_out'],o['sort_order'],o['id']))
                take=max(group['min_select'],min(group['max_select'],max(0,wanted-len(picked))))
                if currentSnapshot and len(options)<group['min_select']:raise ValueError('재주문 기본 사례에 필수 품절 메뉴가 섞였습니다: '+menuAlias)
                picked.extend(options[:take])
            delta=sum(o['price_delta'] for o in picked);unit=price+delta;amount=unit*quantity
            reorderItems.append(dict(menuId=source['id'],menuAlias=menuAlias,quantity=quantity,optionItemIds=[o['id'] for o in picked],snapshotBasePrice=price))
            name=source['name'] if currentSnapshot or variant!=3 else '주문 당시 저장한 아주 긴 이름의 가상 프리미엄 계절 음료 메뉴'
            self.add('order_line',label+f'-L{index+1}',dict(id=lid,order_id=oid,menu_item_id=source['id'],menu_name_snapshot=name,base_price_snapshot=price,unit_price=unit,quantity=quantity,line_amount=amount,created_at=created))
            for k,option in enumerate(picked):
                self.serial['line-option']+=1
                self.add('order_line_option',label+f'-L{index+1}-O{k+1}',dict(id=self.idFor('line-option',self.serial['line-option']),order_line_id=lid,option_item_id=option['id'],option_group_name_snapshot='주문 당시 옵션',option_item_name_snapshot=option['name'],price_delta_snapshot=option['price_delta']))
            row['subtotal_amount']+=amount
        row['total_amount']=row['subtotal_amount']
        timeline=[('PENDING_PAYMENT',created)]
        if placed: timeline.append(('REQUESTED',placed))
        for step,col in [('ACCEPTED','accepted_at'),('MAKING','making_at'),('READY','ready_at'),('COMPLETED','completed_at'),('CANCELED','canceled_at'),('REJECTED','rejected_at'),('EXPIRED','expired_at')]:
            if row[col]: timeline.append((step,row[col]))
        previous=None
        for step,at in timeline:
            self.serial['history']+=1
            self.add('order_status_history',label+'-H-'+step,dict(id=self.idFor('history',self.serial['history']),order_id=oid,from_status=previous,to_status=step,actor_type='SYSTEM',actor_id=None,reason='QA 기준 이력',created_at=at));previous=step
        refundStatus=refundStatus or ('SUCCEEDED' if status in ['CANCELED','REJECTED'] else None)
        for attempt in range(1,attempts+1):
            self.serial['payment']+=1;pid=self.idFor('payment',self.serial['payment'])
            failed=allFailed or attempt<attempts or status=='EXPIRED' and not systemRefund
            pstatus='FAILED' if failed else 'UNKNOWN' if unknown else 'READY' if status=='PENDING_PAYMENT' else 'CANCELED' if refundStatus=='SUCCEEDED' else 'APPROVED'
            approved=pstatus in ['APPROVED','CANCELED']
            self.add('payment',label+f'-P{attempt}',dict(id=pid,order_id=oid,store_id=store,merchant_pg_id=self.idFor('pg',storeIndex),pg_provider='FAKE',attempt_no=attempt,pg_order_id=f'QA-{n:06}-{attempt}',payment_key=f'qa-synthetic-payment-{n}-{attempt}' if approved else None,method=['CARD','KAKAOPAY','NAVERPAY','TOSSPAY'][n%4] if approved else None,amount=row['total_amount'],status=pstatus,approved_at=created+timedelta(minutes=20,seconds=30) if systemRefund else created+timedelta(seconds=5) if approved else None,failed_at=created+timedelta(seconds=attempt) if failed else None,canceled_at=created+timedelta(minutes=22 if systemRefund else 2) if pstatus=='CANCELED' else None,failure_code='QA_SIMULATED_FAILURE' if failed else None,failure_message='QA 의도된 실패' if failed else None,confirm_idempotency_key=f'qa-confirm-{n}-{attempt}',version=0,portone_store_id=None,portone_channel_key=None,created_at=created,updated_at=self.baseTime))
            if attempt==attempts and refundStatus:
                self.serial['refund']+=1
                self.add('payment_cancel',label+'-refund',dict(id=self.idFor('refund',self.serial['refund']),payment_id=pid,cancel_amount=row['total_amount'],cancel_reason='QA 전액 환불',requested_by_type='SYSTEM' if systemRefund else 'CUSTOMER' if status=='CANCELED' else 'STAFF',status=refundStatus,idempotency_key=f'qa-refund-{n}',pg_transaction_key=f'qa-refund-transaction-{n}' if refundStatus=='SUCCEEDED' else None,attempt_count=1,next_retry_at=self.baseTime+timedelta(hours=self.sessionHours) if refundStatus=='REQUESTED' else None,last_error_code='QA_SIMULATED_REFUND_FAILURE' if refundStatus=='FAILED' else None,last_error_message='QA 의도된 환불 실패' if refundStatus=='FAILED' else None,succeeded_at=created+timedelta(minutes=22 if systemRefund else 2) if refundStatus=='SUCCEEDED' else None,created_at=created+timedelta(minutes=21 if systemRefund else 1),updated_at=self.baseTime))
        self.orders.append(dict(alias=label,orderId=oid,account=account,storeAlias=f'S{storeIndex:02}',status=status,lines=lineCount,totalAmount=row['total_amount'],pickupNo=number,expiresAt=row['expires_at'].isoformat(),refundStatus=refundStatus,paymentAttempts=attempts,reorderItems=reorderItems))

    def validate(self):
        for table in ['member','store','menu_item','orders','payment']:
            ids=[f.row['id'] for f in self.rows if f.table==table]
            assert len(ids)==len(set(ids)),table
        orders={f.row['id']:f.row for f in self.rows if f.table=='orders'}
        totals=Counter()
        for f in self.rows:
            if f.table=='order_line':
                assert 1<=f.row['quantity']<=20
                assert f.row['line_amount']==f.row['unit_price']*f.row['quantity']
                totals[f.row['order_id']]+=f.row['line_amount']
        assert all(row['total_amount']==totals[oid] for oid,row in orders.items())
        assert len([f for f in self.rows if f.table=='menu_item' and f.row['store_id']==self.idFor('store',1)])==48
        assert sum(1 for o in self.orders if o['storeAlias']=='S01' and o['status'] in ['REQUESTED','ACCEPTED','MAKING'])==8

    def manifest(self):
        return dict(version=VERSION,baseTime=self.baseTime.isoformat(),databaseTimeZone='UTC',apiOrderTimeZone='+09:00',sessionHours=self.sessionHours,developmentOnly=True,revision=2,isolationRequired=True,reorderCases=self.reorderCases,backendBaseline='f603843',password=PASSWORD,verificationCode='123456',cdnBaseUrlRequired=IMAGE_BASE,counts=dict(Counter(f.table for f in self.rows)),schools=[dict(alias='A',schoolId='g.eulji.ac.kr 도메인의 기존 school_id'),dict(alias='B',schoolId=self.idFor('school',2)),dict(alias='C',schoolId=self.idFor('school',3))],stores=self.storeScenarios,accounts=self.accounts,signupEmails=self.signupEmails,options=self.options,orders=self.orders,ids={f.alias:dict(table=f.table,**{k:v for k,v in f.row.items() if k in ['id','store_id','menu_item_id','option_group_id']}) for f in self.rows if f.table in ['store','category','menu_item','option_group','option_item','member','staff_account']},assumptions=['사용자 확인: O1~O11 권장 조합 구성으로 확정','사용자 확인: S01 커피 4개 추가해 총 48개로 확정','영업 상태 표현은 OPEN + PAUSED 3 + CLOSED 3 = 7개 시나리오','S10 자정 넘김은 DB 제약으로 불가; 22:00~23:59:59 대체임을 명시','S08 끝 시각 null은 is_closed=true일 때만 허용','PENDING_PAYMENT 유지용 기한은 기본 8시간; 일반 주문 API의 20분과 구분'],unsupported=['좌표·거리·도보시간·영업 재개/마감 시각·매장 목록 대기수/메뉴수·휴일 달력','판매자 필수 필드 null: 전화·사업자번호·주소·상호·대표','무제한 maxSelect=null, 음수 옵션 추가금','UNCLAIMED/REFUNDED 주문 상태, 수령 기한, 포장/매장 섭취','카드 마스킹 번호·승인번호 응답','isPopular 필드·최근 7일 인기 자동 배치; isNew는 저장된 Boolean','100자를 초과하는 요청 메모','실제 PortOne PY009/PY012/PY013 제어; 보상 거절은 PR #21/V5 반영 필요','503 MAINTENANCE 모드/종료 시각','현재 BE의 빈 매장·빈 카테고리 자동 숨김'])


def guard(expression,label):
    return f"-- ASSERT: {label}\nINSERT INTO qa_guard(label,ok) SELECT {sqlValue(label)},IF(({expression}),1,0);"


def startSql(database, environment):
    if not re.fullmatch(r'[A-Za-z0-9_]{1,64}',database): raise ValueError('DB 이름은 영문·숫자·밑줄만 허용합니다.')
    if re.search(r'prod|production|live',database,re.I): raise ValueError('운영으로 보이는 DB 이름은 사용할 수 없습니다.')
    if environment not in ['dev','staging','test']: raise ValueError('개발/스테이징/격리 테스트 전용입니다.')
    return ["-- Generated QA fixtures only. ISOLATED DEVELOPMENT/STAGING ONLY; NEVER PUBLIC SERVER. Run mysql in batch mode WITHOUT --force. Close QA apps during reset.","SET NAMES utf8mb4;","SET time_zone='+00:00';","DROP TEMPORARY TABLE IF EXISTS qa_guard;","CREATE TEMPORARY TABLE qa_guard(label VARCHAR(200) NOT NULL,ok INT NOT NULL CHECK(ok=1));",guard(f'DATABASE()={sqlValue(database)}','TARGET_DATABASE_MISMATCH'),guard("@@character_set_database='utf8mb4'",'DATABASE_MUST_BE_UTF8MB4'),"CREATE TABLE IF NOT EXISTS ormo_qa_seed_registry(table_name VARCHAR(50) NOT NULL,entity_id BIGINT NOT NULL,alias_name VARCHAR(100) NOT NULL,fixture_version VARCHAR(40) NOT NULL,base_time DATETIME(6) NOT NULL,PRIMARY KEY(table_name,entity_id),UNIQUE KEY uk_qa_alias(alias_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",guard("GET_LOCK('ormo-android-qa-v1',10)=1",'QA_LOCK_BUSY'),"START TRANSACTION;","SET @qaSchoolA=(SELECT school_id FROM school_email_domain WHERE domain='g.eulji.ac.kr');",guard('@qaSchoolA IS NOT NULL','RUN_FLYWAY_V2_FIRST')]


def registryInsert(f,baseTime):
    return upsert('ormo_qa_seed_registry',dict(table_name=f.table,entity_id=f.row['id'],alias_name=f.alias,fixture_version=VERSION,base_time=baseTime),('table_name','entity_id'))


def ownedCleanupRow(table, alias):
    # Only rows already reachable from registered synthetic customers may be rebuilt.
    member="SELECT entity_id FROM ormo_qa_seed_registry WHERE table_name='member' AND fixture_version='ormo-android-qa-v1'"
    if table=='orders':return f'{alias}.member_id IN ({member})'
    if table in ['order_line','order_status_history','payment']:
        return f'EXISTS(SELECT 1 FROM orders qo WHERE qo.id={alias}.order_id AND qo.member_id IN ({member}))'
    if table=='order_line_option':return f'EXISTS(SELECT 1 FROM order_line ql JOIN orders qo ON qo.id=ql.order_id WHERE ql.id={alias}.order_line_id AND qo.member_id IN ({member}))'
    if table=='payment_cancel':return f'EXISTS(SELECT 1 FROM payment qp JOIN orders qo ON qo.id=qp.order_id WHERE qp.id={alias}.payment_id AND qo.member_id IN ({member}))'
    return 'FALSE'


def collisionChecks(cat):
    sql=[]
    grouped=defaultdict(list)
    for f in cat.rows:
        if 'id' in f.row: grouped[f.table].append(f)
    for table,fixtures in grouped.items():
        ids=','.join(str(f.row['id']) for f in fixtures)
        sql.append(guard(f"NOT EXISTS(SELECT 1 FROM `{table}` t LEFT JOIN ormo_qa_seed_registry r ON r.table_name={sqlValue(table)} AND r.entity_id=t.id AND r.fixture_version={sqlValue(VERSION)} WHERE t.id IN ({ids}) AND r.entity_id IS NULL AND NOT ({ownedCleanupRow(table,'t')}))",'UNOWNED_ID_COLLISION_'+table))
    # Natural-key collisions must fail before ON DUPLICATE KEY could update a non-QA row.
    for table,column in [('member','email'),('staff_account','login_id'),('merchant','business_reg_no'),('school_email_domain','domain')]:
        vals=[sqlValue(f.row[column]) for f in grouped.get(table,[]) if f.row.get(column) is not None]
        if vals:
            sql.append(guard(f"NOT EXISTS(SELECT 1 FROM `{table}` t LEFT JOIN ormo_qa_seed_registry r ON r.table_name={sqlValue(table)} AND r.entity_id=t.id AND r.fixture_version={sqlValue(VERSION)} WHERE t.`{column}` IN ({','.join(vals)}) AND r.entity_id IS NULL)",'UNOWNED_NATURAL_KEY_'+table))
    uniqueKeys={'school':[('name','campus')],'store':[('school_id','name')],'category':[('store_id','name')],'menu_item':[('store_id','name')],'option_group':[('menu_item_id','name')],'option_item':[('option_group_id','name')],'orders':[('order_code',),('member_id','idempotency_key')],'payment':[('pg_order_id',),('payment_key',),('confirm_idempotency_key',),('order_id','attempt_no')],'payment_cancel':[('idempotency_key',)],'terms_agreement':[('member_id','terms_type','terms_version')]}
    for table,keysList in uniqueKeys.items():
        predicates=[]
        for fixture in grouped.get(table,[]):
            for keys in keysList:
                if any(fixture.row.get(key) is None for key in keys): continue
                condition=' AND '.join(f't.`{key}`={sqlValue(fixture.row[key])}' for key in keys)
                predicates.append(f'({condition} AND t.id<>{fixture.row["id"]})')
        if predicates:
            sql.append(guard(f'NOT EXISTS(SELECT 1 FROM `{table}` t WHERE '+ ' OR '.join(predicates)+')','NATURAL_KEY_POINTS_TO_ANOTHER_ID_'+table))
    # Signup mailbox reservations are registry rows, not pre-registered customer accounts.
    for i,email in enumerate(cat.signupEmails,1):
        sql.append(guard(f"NOT EXISTS(SELECT 1 FROM member WHERE email={sqlValue(email)}) OR EXISTS(SELECT 1 FROM ormo_qa_seed_registry WHERE table_name='signup_email' AND entity_id={i} AND fixture_version={sqlValue(VERSION)})",'UNRESERVED_SIGNUP_MAILBOX'))
    return sql


def scopedOrderCleanup(memberExpr, onlyRuntime=False):
    sql=["DROP TEMPORARY TABLE IF EXISTS qa_order_cleanup;","CREATE TEMPORARY TABLE qa_order_cleanup(id BIGINT PRIMARY KEY);"]
    extra=" AND NOT EXISTS(SELECT 1 FROM ormo_qa_seed_registry r WHERE r.table_name='orders' AND r.entity_id=o.id AND r.fixture_version='ormo-android-qa-v1')" if onlyRuntime else ''
    sql.append(f'INSERT INTO qa_order_cleanup SELECT o.id FROM orders o WHERE o.member_id IN ({memberExpr}){extra};')
    for table,join in [('payment_reconciliation','JOIN payment p ON p.id=t.payment_id JOIN qa_order_cleanup q ON q.id=p.order_id'),('payment_cancel','JOIN payment p ON p.id=t.payment_id JOIN qa_order_cleanup q ON q.id=p.order_id'),('notification_log','JOIN qa_order_cleanup q ON q.id=t.order_id'),('order_line_discount','JOIN order_line l ON l.id=t.order_line_id JOIN qa_order_cleanup q ON q.id=l.order_id'),('order_line_option','JOIN order_line l ON l.id=t.order_line_id JOIN qa_order_cleanup q ON q.id=l.order_id'),('order_line','JOIN qa_order_cleanup q ON q.id=t.order_id'),('order_status_history','JOIN qa_order_cleanup q ON q.id=t.order_id'),('payment','JOIN qa_order_cleanup q ON q.id=t.order_id')]:
        sql.append(f'DELETE t FROM `{table}` t {join};')
    if onlyRuntime: sql.append('DELETE o FROM orders o JOIN qa_order_cleanup q ON q.id=o.id;')
    else:
        # Retain parent IDs for upsert; free the owned pickup constraints before rebasing dates.
        sql.append("UPDATE orders o JOIN qa_order_cleanup q ON q.id=o.id SET o.status='PENDING_PAYMENT',o.pickup_no=NULL,o.placed_at=NULL,o.business_date=NULL;")
        sql.append("DELETE o FROM orders o JOIN qa_order_cleanup q ON q.id=o.id LEFT JOIN ormo_qa_seed_registry r ON r.table_name='orders' AND r.entity_id=o.id AND r.fixture_version='ormo-android-qa-v1' WHERE r.entity_id IS NULL;")
    return sql


def makeSql(cat,database,environment,mode='seed_all'):
    sql=startSql(database,environment)+collisionChecks(cat)
    full=mode=='seed_all'
    if not full: sql.append(guard("EXISTS(SELECT 1 FROM ormo_qa_seed_registry WHERE table_name='store' AND fixture_version='ormo-android-qa-v1')",'RUN_SEED_ALL_FIRST'))
    memberIds=','.join(str(a['memberId']) for a in cat.accounts)
    consumables=','.join(str(a['memberId']) for a in cat.accounts if a['consumable'])
    signupList=','.join(sqlValue(e) for e in cat.signupEmails)
    # Newly registered customers on reserved QA addresses are owned, consumable data.
    sql += ["DROP TEMPORARY TABLE IF EXISTS qa_signup_cleanup;","CREATE TEMPORARY TABLE qa_signup_cleanup(id BIGINT PRIMARY KEY);",f'INSERT INTO qa_signup_cleanup SELECT id FROM member WHERE email IN ({signupList});']
    sql += scopedOrderCleanup('SELECT id FROM qa_signup_cleanup',onlyRuntime=True)
    sql += ["DELETE r FROM refresh_token r JOIN qa_signup_cleanup q ON r.subject_type='MEMBER' AND r.subject_id=q.id;","DELETE n FROM notification_log n JOIN device_token d ON d.id=n.device_token_id JOIN qa_signup_cleanup q ON d.owner_type='MEMBER' AND d.owner_id=q.id;","DELETE d FROM device_token d JOIN qa_signup_cleanup q ON d.owner_type='MEMBER' AND d.owner_id=q.id;","DELETE a FROM terms_agreement a JOIN qa_signup_cleanup q ON a.member_id=q.id;","DELETE m FROM member m JOIN qa_signup_cleanup q ON m.id=q.id;"]
    sql += scopedOrderCleanup(memberIds)
    sql.append("DELETE FROM ormo_qa_seed_registry WHERE fixture_version='ormo-android-qa-v1' AND table_name IN ('order_line','order_line_option','order_status_history','payment','payment_cancel','email_verification');")
    # Stateless access JWTs are not blacklisted; clients must log out before reset.
    resetIds=memberIds if full else consumables
    sql += [f"DELETE FROM refresh_token WHERE subject_type='MEMBER' AND subject_id IN ({resetIds});",f"UPDATE device_token SET is_active=0,deactivated_at={sqlValue(cat.baseTime)} WHERE owner_type='MEMBER' AND owner_id IN ({resetIds});"]
    qaEmails=[a['email'] for a in cat.accounts]+cat.signupEmails+cat.verificationEmails
    sql.append('DELETE FROM email_verification WHERE email IN ('+','.join(sqlValue(e) for e in qaEmails)+');')
    for f in cat.rows:
        scenario=(f.table,f.row.get('id')) in cat.beforeReorder
        if not full and not scenario and f.table not in ['member','terms_agreement','orders','order_line','order_line_option','order_status_history','payment','payment_cancel','email_verification']: continue
        if not full and f.table=='member' and f.row['id'] not in [a['memberId'] for a in cat.accounts if a['consumable']]: continue
        keys={'store_business_hour':('store_id','day_of_week'),'staff_store':('staff_account_id','store_id'),'menu_item_category':('menu_item_id','category_id')}.get(f.table,('id',))
        sql.append(upsert(f.table,cat.beforeReorder.get((f.table,f.row.get('id')),f.row),keys))
        if 'id' in f.row: sql.append(registryInsert(f,cat.baseTime))
    for f in cat.rows:
        if (f.table,f.row.get('id')) in cat.beforeReorder:sql.append(upsert(f.table,f.row))
    for i,email in enumerate(cat.signupEmails,1):
        sql.append(upsert('ormo_qa_seed_registry',dict(table_name='signup_email',entity_id=i,alias_name=email,fixture_version=VERSION,base_time=cat.baseTime),('table_name','entity_id')))
    sql += ["INSERT INTO order_daily_counter(store_id,business_date,last_pickup_no,updated_at) SELECT o.store_id,o.business_date,MAX(o.pickup_no),UTC_TIMESTAMP(6) FROM orders o JOIN ormo_qa_seed_registry r ON r.table_name='store' AND r.entity_id=o.store_id AND r.fixture_version='ormo-android-qa-v1' WHERE o.business_date IS NOT NULL GROUP BY o.store_id,o.business_date ON DUPLICATE KEY UPDATE last_pickup_no=GREATEST(last_pickup_no,VALUES(last_pickup_no)),updated_at=VALUES(updated_at);","COMMIT;","SELECT RELEASE_LOCK('ormo-android-qa-v1');"]
    return '\n'.join(sql)+'\n'


def advanceSql(cat,database,environment,orderId,toStatus,at):
    if toStatus not in FORWARD: raise ValueError('목표 상태는 ACCEPTED/MAKING/READY/COMPLETED 등 정방향 접수 상태만 지원합니다. 결제 대기는 DEV-1을 먼저 사용하세요.')
    sql=startSql(database,environment)
    oid=int(orderId);target=FORWARD.index(toStatus)
    allowed=','.join(sqlValue(s) for s in FORWARD[:target+1])
    sql += [f'SELECT id FROM orders WHERE id={oid} FOR UPDATE;',guard(f"EXISTS(SELECT 1 FROM orders o JOIN ormo_qa_seed_registry r ON r.table_name='member' AND r.entity_id=o.member_id AND r.fixture_version={sqlValue(VERSION)} WHERE o.id={oid} AND o.status IN ({allowed}) AND o.placed_at<={sqlValue(at)} AND COALESCE(o.completed_at,o.ready_at,o.making_at,o.accepted_at,o.placed_at)<={sqlValue(at)})",'ORDER_NOT_OWNED_OR_INVALID_TRANSITION')]
    columns=[('ACCEPTED','accepted_at'),('MAKING','making_at'),('READY','ready_at'),('COMPLETED','completed_at')]
    for index,(step,column) in enumerate(columns[:target]):
        following=[col for _,col in columns[index:]]
        filled='COALESCE('+','.join('o.`'+col+'`' for col in following)+','+sqlValue(at)+')'
        previous=FORWARD[index]
        sql.append(f"INSERT INTO order_status_history(order_id,from_status,to_status,actor_type,actor_id,reason,created_at) SELECT o.id,{sqlValue(previous)},{sqlValue(step)},'SYSTEM',NULL,'QA 수동 진행',{filled} FROM orders o WHERE o.id={oid} AND o.`{column}` IS NULL AND NOT EXISTS(SELECT 1 FROM order_status_history h WHERE h.order_id=o.id AND h.to_status={sqlValue(step)});")
        sql.append(f"UPDATE orders o SET `{column}`={filled} WHERE o.id={oid};")
    sql += [f"UPDATE orders SET status={sqlValue(toStatus)},version=version+1,updated_at={sqlValue(at)} WHERE id={oid} AND status<>{sqlValue(toStatus)};","COMMIT;","SELECT RELEASE_LOCK('ormo-android-qa-v1');"]
    return '\n'.join(sql)+'\n'


def prepareVerificationSql(cat,database,environment,email,purpose,scenario):
    allowed=set(cat.signupEmails+cat.verificationEmails+[a['email'] for a in cat.accounts if a['consumable']])
    if email not in allowed: raise ValueError('명시적으로 등록한 QA 이메일만 허용합니다.')
    if purpose not in ['SIGNUP','PASSWORD_RESET'] or scenario not in ['valid','wrong','expired','resend','daily']: raise ValueError('지원하지 않는 인증 시나리오입니다.')
    sql=startSql(database,environment)
    sql += [guard("EXISTS(SELECT 1 FROM ormo_qa_seed_registry WHERE table_name='store' AND fixture_version='ormo-android-qa-v1')",'RUN_SEED_ALL_FIRST'),f'DELETE FROM email_verification WHERE email={sqlValue(email)};']
    before=len(cat.rows);cat.addVerification(email,purpose,scenario,500)
    for f in cat.rows[before:]:
        row=dict(f.row);row.pop('id')  # Runtime ID, separate from fixed baseline verification rows.
        cols=','.join('`'+k+'`' for k in row)
        sql.append(f'INSERT INTO email_verification({cols}) VALUES ({",".join(sqlValue(v) for v in row.values())});')
    sql += ['COMMIT;',"SELECT RELEASE_LOCK('ormo-android-qa-v1');"]
    return '\n'.join(sql)+'\n'


def verifySql(cat):
    def check(name,query,expected): return f'SELECT {sqlValue(name)} AS check_name,({query}) AS actual,{expected} AS expected;'
    own="JOIN ormo_qa_seed_registry r ON r.entity_id=t.id AND r.table_name='{table}' AND r.fixture_version='ormo-android-qa-v1'"
    rows=["SET time_zone='+00:00';",check('학교 A/B/C',"SELECT COUNT(DISTINCT school_id) FROM store t "+own.format(table='store'),3)]
    for table in ['store','member','menu_item','orders']:
        rows.append(check('QA '+table,'SELECT COUNT(*) FROM `'+table+'` t '+own.format(table=table),sum(f.table==table for f in cat.rows)))
    s1=cat.idFor('store',1)
    for name,query,expected in [('S01 메뉴',f'SELECT COUNT(*) FROM menu_item WHERE store_id={s1}',48),('S01 카테고리',f'SELECT COUNT(*) FROM category WHERE store_id={s1}',7),('S01 이미지 null',f'SELECT COUNT(*) FROM menu_item WHERE store_id={s1} AND image_key IS NULL',4),('S01 의도적 404',f"SELECT COUNT(*) FROM menu_item WHERE store_id={s1} AND image_key LIKE 'ormo-qa-missing-%'",2),('S01 대기',f"SELECT COUNT(*) FROM orders WHERE store_id={s1} AND status IN ('REQUESTED','ACCEPTED','MAKING')",8),('S02 대기',f"SELECT COUNT(*) FROM orders WHERE store_id={cat.idFor('store',2)} AND status IN ('REQUESTED','ACCEPTED','MAKING')",0)]: rows.append(check(name,query,expected))
    for alias,count in [('qa-heavy',150),('qa-page20',20),('qa-page21',21),('qa-filtered',30)]: rows.append(check(alias,f"SELECT COUNT(*) FROM orders WHERE member_id={cat.byAlias[alias].row['id']}",count))
    rows.append(check('S01 직접 품절',f'SELECT COUNT(*) FROM menu_item WHERE store_id={s1} AND is_sold_out=1',9))
    rows.append(check('S01 빈 이름 카테고리',f"SELECT COUNT(*) FROM category WHERE store_id={s1} AND name=''",1))
    rows.append(check('S01 메뉴 없는 카테고리',f'SELECT COUNT(*) FROM category c WHERE c.store_id={s1} AND NOT EXISTS(SELECT 1 FROM menu_item_category m WHERE m.category_id=c.id)',1))
    rows.append(check('S01 배지·품절 조합',f'SELECT COUNT(DISTINCT is_new,is_event,is_sold_out) FROM menu_item WHERE store_id={s1}',8))
    rows.append(check('QA 주문 상태 9개',"SELECT COUNT(DISTINCT t.status) FROM orders t "+own.format(table='orders'),9))
    for alias in ['qa-live1','qa-live3','qa-live5','qa-wd-block']:
        expected={'qa-live1':1,'qa-live3':3,'qa-live5':5,'qa-wd-block':1}[alias]
        rows.append(check(alias+' 진행',f"SELECT COUNT(*) FROM orders WHERE member_id={cat.byAlias[alias].row['id']} AND status IN ('PENDING_PAYMENT','REQUESTED','ACCEPTED','MAKING','READY') AND (status<>'PENDING_PAYMENT' OR expires_at>{sqlValue(cat.baseTime)})",expected))
    for prefix in ['qa-wd','qa-pw']:
        ids=','.join(str(a['memberId']) for a in cat.accounts if a['consumable'] and a['alias'].startswith(prefix))
        rows.append(check(prefix+' 여유 계정',f"SELECT COUNT(*) FROM member WHERE id IN ({ids}) AND status='ACTIVE'",5))
    rows.append(check('가입용 미등록 이메일', 'SELECT COUNT(*) FROM member WHERE email IN ('+','.join(sqlValue(e) for e in cat.signupEmails)+')',0))
    for alias,age,new in [('M0112',0,1),('M0113',13,1),('M0114',14,0)]:
        rows.append(check(alias+' 신규 경계',f"SELECT COUNT(*) FROM menu_item WHERE id={cat.byAlias[alias].row['id']} AND TIMESTAMPDIFF(DAY,created_at,{sqlValue(cat.baseTime)})={age} AND is_new={new}",1))
    rows.append(check('항목·주문 합계 불일치',"SELECT COUNT(*) FROM orders t "+own.format(table='orders')+" WHERE t.total_amount<>(SELECT COALESCE(SUM(l.line_amount),0) FROM order_line l WHERE l.order_id=t.id)",0))
    rows.append(check('전액 환불 금액 불일치',"SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id JOIN orders t ON t.id=p.order_id "+own.format(table='orders')+' WHERE c.cancel_amount<>p.amount',0))
    rows.append(check('재시도 5개인 주문',"SELECT COUNT(*) FROM (SELECT t.id FROM orders t "+own.format(table='orders')+" JOIN payment p ON p.order_id=t.id GROUP BY t.id HAVING COUNT(*)=5) attempts",2))
    for case in cat.options:
        rows.append(check(case['alias']+' 그룹',f"SELECT COUNT(*) FROM option_group WHERE menu_item_id={case['menuId']}",len(case['groupIds'])))
    heavy=cat.byAlias['qa-heavy'].row['id']
    rows.append(check('qa-heavy S01',f'SELECT COUNT(*) FROM orders WHERE member_id={heavy} AND store_id={s1}',80))
    rows.append(check('qa-heavy S02',f'SELECT COUNT(*) FROM orders WHERE member_id={heavy} AND store_id={cat.idFor("store",2)}',70))
    rows.append(check('qa-heavy S01 줄 수 4종',f'SELECT COUNT(DISTINCT line_count) FROM (SELECT o.id,COUNT(l.id) AS line_count FROM orders o JOIN order_line l ON l.order_id=o.id WHERE o.member_id={heavy} AND o.store_id={s1} GROUP BY o.id) counts',4))
    for account,count in [('qa-page20',20),('qa-page21',21),('qa-filtered',30),('qa-wd-block',1)]:
        rows.append(check(account+' S01',f'SELECT COUNT(*) FROM orders WHERE member_id={cat.byAlias[account].row["id"]} AND store_id={s1}',count))
    for case in cat.reorderCases:
        rows.append(check(case['alias']+' 완료',f"SELECT COUNT(*) FROM orders WHERE id={case['orderId']} AND status='COMPLETED'",1))
        rows.append(check(case['alias']+' 줄 수',f"SELECT COUNT(*) FROM order_line WHERE order_id={case['orderId']}",case['lines']))
    for account in ['qa-longname-ko','qa-longname-en']:
        rows.append(check(account+' 이름20자',f'SELECT CHAR_LENGTH(name) FROM member WHERE id={cat.byAlias[account].row["id"]}',20))
    multi=cat.byAlias['qa-multistore'].row['id']
    for store,n in [(1,2),(8,2),(2,1)]:
        rows.append(check(f'qa-multistore S{store:02}',f"SELECT COUNT(*) FROM orders WHERE member_id={multi} AND store_id={cat.idFor('store',store)} AND status='COMPLETED'",n))
    rows.append(check('S01 R1/R2 전용 품절',f'SELECT COUNT(*) FROM menu_item WHERE id IN ({cat.byAlias["M0122"].row["id"]},{cat.byAlias["M0123"].row["id"]},{cat.byAlias["M0124"].row["id"]}) AND is_sold_out=1',3))
    rows.append(check('R3 HIDDEN',f'SELECT COUNT(*) FROM menu_item WHERE id={cat.byAlias["M0127"].row["id"]} AND status='+sqlValue('HIDDEN'),1))
    rows.append(check('R4 가격 상승',f'SELECT COUNT(*) FROM order_line l JOIN menu_item m ON m.id=l.menu_item_id WHERE l.order_id={cat.reorderCases[3]["orderId"]} AND l.base_price_snapshot<m.base_price',1))
    rows.append(check('R5 과거 옵션 품절',f'SELECT COUNT(*) FROM order_line l JOIN order_line_option lo ON lo.order_line_id=l.id JOIN option_item oi ON oi.id=lo.option_item_id WHERE l.order_id={cat.reorderCases[4]["orderId"]} AND oi.is_sold_out=1',1))
    rows.append("SELECT o.status,COUNT(*) AS qa_count FROM orders o JOIN ormo_qa_seed_registry r ON r.table_name='orders' AND r.entity_id=o.id AND r.fixture_version='ormo-android-qa-v1' GROUP BY o.status;")
    orderable=[]
    for fixture in cat.rows:
        if fixture.table!='menu_item':continue
        menu=fixture.row
        if menu['store_id']!=s1 or menu['status']!='ACTIVE' or menu['is_sold_out']:continue
        groups=[f.row for f in cat.rows if f.table=='option_group' and f.row['menu_item_id']==menu['id']]
        if all(sum(f.table=='option_item' and f.row['option_group_id']==g['id'] and not f.row['is_sold_out'] for f in cat.rows)>=g['min_select'] for g in groups):orderable.append(menu['id'])
    assert len(orderable)>=25
    rows.append(check('S01 구매 가능(최소25)',f"SELECT COUNT(*) FROM menu_item m WHERE m.store_id={s1} AND m.status='ACTIVE' AND m.is_sold_out=0 AND NOT EXISTS(SELECT 1 FROM option_group g WHERE g.menu_item_id=m.id AND (SELECT COUNT(*) FROM option_item i WHERE i.option_group_id=g.id AND i.is_sold_out=0)<g.min_select)",len(orderable)))
    rows.append(f"SELECT l.menu_item_id,SUM(l.quantity) AS recent_7d_quantity FROM order_line l JOIN orders o ON o.id=l.order_id WHERE o.store_id={s1} AND o.status='COMPLETED' AND o.completed_at>={sqlValue(cat.baseTime-timedelta(days=7))} GROUP BY l.menu_item_id ORDER BY recent_7d_quantity DESC,l.menu_item_id LIMIT 5;")
    return '\n'.join(rows)+'\n'


def writeBundle(cat,out,database,environment):
    out.mkdir(parents=True,exist_ok=True)
    (out/'seed_all.sql').write_text(makeSql(cat,database,environment),encoding='utf-8')
    (out/'reset_consumables.sql').write_text(makeSql(cat,database,environment,'reset_consumables'),encoding='utf-8')
    (out/'verify.sql').write_text(verifySql(cat),encoding='utf-8')
    manifest=cat.manifest();manifest.update(targetDatabase=None if database=='DB_NAME_REQUIRED' else database,targetDatabasePlaceholder=database if database=='DB_NAME_REQUIRED' else None,targetDatabaseConfirmed=database!='DB_NAME_REQUIRED',targetEnvironment=environment,remoteDatabaseApplied=False)
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    (out/'reorder-cases.json').write_text(json.dumps(cat.reorderCases,ensure_ascii=False,indent=2))
    csvRows={'accounts':cat.accounts,'stores':cat.storeScenarios,'orders':cat.orders,'options':cat.options,'menus':[dict(alias=f.alias,menuId=f.row['id'],storeId=f.row['store_id'],name=f.row['name'],basePrice=f.row['base_price'],soldOut=f.row['is_sold_out'],isNew=f.row['is_new'],isEvent=f.row['is_event'],imageKey=f.row['image_key']) for f in cat.rows if f.table=='menu_item']}
    for name,rows in csvRows.items():
        with (out/(name+'.csv')).open('w',encoding='utf-8-sig',newline='') as handle:
            writer=csv.DictWriter(handle,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    imageList=[dict(url=f'{IMAGE_BASE}/seed/ormo-qa-{i:02}/480/360.jpg',expectedStatus=200) for i in range(1,31)]+[dict(url=f'{IMAGE_BASE}/ormo-qa-missing-{i}',expectedStatus=404) for i in [47,48]]
    (out/'image-urls.json').write_text(json.dumps(imageList,ensure_ascii=False,indent=2))
    mocks=dict(warning='FE 전용 방어/미지원 상태 예시. 실제 API 계약이 아니며 DB에 넣지 않는다.',backendPersistence=False,stores=[dict(alias=s['alias'],status=s['status'],requestedReopenAt=s['requestedReopenAt']) for s in cat.storeScenarios[:7]],unsupportedSellerNulls=[dict(alias='S03',contactPhone=None),dict(alias='S04',businessRegNo=None),dict(alias='S10',businessName='QA 가상상호',representativeName='가상대표',businessRegNo=None,businessAddress=None,contactPhone=None,mailOrderRegNo=None)],overnightHours=dict(alias='S10',openTime='22:00:00',closeTime='02:00:00',nextDay=True),unlimitedOption=dict(case='O6',maxSelect=None),negativeOption=dict(priceDelta=-500,expected='DB rejects'),missingOrderStates=['UNCLAIMED','REFUNDED'],paymentDisplay=[dict(method=m,maskedCardNumber='0000-****-****-0000' if m=='CARD' else None,approvalNumber='QA000001' if m=='CARD' else None) for m in ['CARD','KAKAOPAY','NAVERPAY','TOSSPAY']],memoTooLong=dict(requestMemo='가'*101,expected='현재 DB VARCHAR(100) 초과. 현행 앱/BE 오류 응답을 별도 확인'),maintenance=dict(httpStatus=503,code='MAINTENANCE',endsAt=(cat.baseTime+timedelta(minutes=35)).isoformat(),implemented=False),paymentConditions=[dict(code=code,mode='PortOne 테스트 또는 별도 FE mock 필요',fakeGatewayCanForce=False) for code in ['PY009','PY012','PY013']])
    (out/'frontend_mock_cases.json').write_text(json.dumps(mocks,ensure_ascii=False,indent=2))



def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['seed_all','reset_consumables','advance_order','prepare_verification','verify'])
    parser.add_argument('--base-time',help='오프셋 필수 ISO 8601; 생략하면 현재 KST')
    parser.add_argument('--database',required=True)
    parser.add_argument('--environment',required=True,choices=['dev','staging','test'])
    parser.add_argument('--output-dir',type=Path,default=Path('outputs/android-qa-seed'))
    parser.add_argument('--session-hours',type=int,default=8)
    parser.add_argument('--order-id',type=int)
    parser.add_argument('--to-status')
    parser.add_argument('--at')
    parser.add_argument('--email')
    parser.add_argument('--purpose',choices=['SIGNUP','PASSWORD_RESET'],default='SIGNUP')
    parser.add_argument('--scenario',choices=['valid','wrong','expired','resend','daily'],default='valid')
    parser.add_argument('--confirm-isolated-target',action='store_true',help='담당자가 외부 공개 서버가 아닌 격리 개발/스테이징 DB임을 확인한 경우만 지정')
    parser.add_argument('--execute',action='store_true',help='명시한 mysql login-path를 이용해 적용. 기본은 파일 생성만.')
    parser.add_argument('--mysql-login-path',help='mysql 클라이언트가 사용자가 준비한 자격증명을 직접 사용. 이 도구는 파일을 열지 않음.')
    args=parser.parse_args()
    try:
        if not 1<=args.session_hours<=24: raise ValueError('--session-hours는 1~24입니다.')
        cat=Catalog(parseTime(args.base_time),args.session_hours)
        startSql(args.database,args.environment)  # Validate target even on read-only generation.
        if args.command in ['seed_all','reset_consumables','verify']:
            writeBundle(cat,args.output_dir,args.database,args.environment)
            path=args.output_dir/(args.command+'.sql')
        elif args.command=='advance_order':
            if args.order_id is None or not args.to_status: raise ValueError('--order-id와 --to-status가 필요합니다.')
            args.output_dir.mkdir(parents=True,exist_ok=True);path=args.output_dir/'advance_order.sql';path.write_text(advanceSql(cat,args.database,args.environment,args.order_id,args.to_status,parseTime(args.at)))
        else:
            if not args.email: raise ValueError('--email이 필요합니다.')
            args.output_dir.mkdir(parents=True,exist_ok=True);path=args.output_dir/'prepare_verification.sql';path.write_text(prepareVerificationSql(cat,args.database,args.environment,args.email,args.purpose,args.scenario))
        if args.execute:
            if args.database in ['DB_NAME_REQUIRED','ISOLATED_DB_NAME']:raise ValueError('실제 격리 DB 이름이 확인되지 않은 검토본입니다. --database에 확인된 이름을 지정해 다시 생성하세요.')
            if args.command!='verify' and not args.confirm_isolated_target:raise ValueError('--execute에는 --confirm-isolated-target 확인이 필요합니다. 외부 공개 서버에 적용하지 마세요.')
            if not args.mysql_login_path or not re.fullmatch(r'[A-Za-z0-9_-]+',args.mysql_login_path): raise ValueError('--execute에는 --mysql-login-path가 필요합니다.')
            with path.open('rb') as source:
                result=subprocess.run(['mysql','--login-path='+args.mysql_login_path,'--default-character-set=utf8mb4','--batch','--database='+args.database],stdin=source,stdout=None if args.command=='verify' else subprocess.DEVNULL,stderr=subprocess.PIPE,check=False)
            if result.returncode:
                # Driver errors might include data or credentials: never echo their raw text.
                raise RuntimeError('SQL 실행 실패. 연결/스키마/충돌 가드를 DBA가 확인하세요. 원문 로그는 출력하지 않았습니다.')
        print(json.dumps(dict(result='executed' if args.execute else 'generated_only',command=args.command,sql=str(path),rows=len(cat.rows),manifest=str(args.output_dir/'manifest.json')),ensure_ascii=False))
    except (ValueError,RuntimeError,OSError,AssertionError) as exc:
        print('QA_SEED_ERROR: '+str(exc),file=sys.stderr);return 1
    return 0


if __name__=='__main__': sys.exit(main())
