import json
import tempfile
import unittest
from collections import Counter
from pathlib import Path
from qa_seed import Catalog, parseTime, makeSql, advanceSql, prepareVerificationSql, writeBundle, BASE, STATES


class QaSeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.now=parseTime('2026-11-25T10:00:00+09:00')
        cls.cat=Catalog(cls.now)

    def testIdsAndSqlAreStable(self):
        second=Catalog(self.now)
        self.assertEqual(self.cat.manifest(),second.manifest())
        self.assertEqual(makeSql(self.cat,'coffeul','test'),makeSql(second,'coffeul','test'))

    def testRequestedCountsAndCategoryDistribution(self):
        c=self.cat
        self.assertEqual(len(c.storeScenarios),13)
        self.assertEqual(sum(f.table=='category' and f.row['store_id']==c.idFor('store',1) for f in c.rows),7)
        for account,n in [('qa-heavy',150),('qa-page20',20),('qa-page21',21),('qa-filtered',30)]:
            self.assertEqual(sum(o['account']==account for o in c.orders),n)
        self.assertEqual(sum(o['account']=='qa-filtered' and o['status']=='EXPIRED' for o in c.orders),25)

    def test_cart_has_at_least_25_valid_choices(self):
        c=self.cat;valid=0
        for f in c.rows:
            if f.table!='menu_item' or f.row['store_id']!=c.idFor('store',1) or f.row['is_sold_out']:continue
            groups=[g.row for g in c.rows if g.table=='option_group' and g.row['menu_item_id']==f.row['id']]
            if all(sum(i.table=='option_item' and i.row['option_group_id']==g['id'] and not i.row['is_sold_out'] for i in c.rows)>=g['min_select'] for g in groups):valid+=1
        self.assertGreaterEqual(valid,25)
        self.assertEqual(len(c.options),11)
        menus=[f.row for f in c.rows if f.table=='menu_item' and f.row['store_id']==c.idFor('store',1)]
        self.assertEqual(len({(m['is_new'],m['is_event'],m['is_sold_out']) for m in menus}),8)

    def testOrderStatesLinesQuantitiesAndMoney(self):
        c=self.cat
        counts=Counter(o['status'] for o in c.orders if o['account']=='qa-states')
        self.assertTrue(all(counts[s]>=3 for s in STATES))
        self.assertTrue({1,2,10,20}.issubset({o['lines'] for o in c.orders}))
        quantities={f.row['quantity'] for f in c.rows if f.table=='order_line'}
        self.assertTrue({1,2,20}.issubset(quantities))
        self.assertTrue(any(o['totalAmount']>=100000 for o in c.orders))
        self.assertTrue(any(o['pickupNo']==999 for o in c.orders))

    def testSnapshotOptionsBelongToLineMenu(self):
        c=self.cat
        lines={f.row['id']:f.row for f in c.rows if f.table=='order_line'}
        groups={f.row['id']:f.row['menu_item_id'] for f in c.rows if f.table=='option_group'}
        options={f.row['id']:f.row for f in c.rows if f.table=='option_item'}
        for f in c.rows:
            if f.table=='order_line_option':
                self.assertEqual(groups[options[f.row['option_item_id']]['option_group_id']],lines[f.row['order_line_id']]['menu_item_id'])

    def testRelativeTimeAndExpiredStateConsistent(self):
        for f in self.cat.rows:
            if f.table=='orders':
                if f.row['status']=='PENDING_PAYMENT':self.assertGreater(f.row['expires_at'],self.now)
                if f.row['status']=='EXPIRED':self.assertLessEqual(f.row['expired_at'],self.now)
                self.assertLessEqual(f.row['created_at'],self.now)
                self.assertLessEqual(len(f.row['request_memo'] or ''),100)

    def test_utf8_images_badges_and_consumables(self):
        c=self.cat
        menus=[f.row for f in c.rows if f.table=='menu_item' and f.row['store_id']==c.idFor('store',1)]
        self.assertEqual(sum(m['image_key'] is None for m in menus),4)
        self.assertEqual(sum((m['image_key'] or '').startswith('ormo-qa-missing') for m in menus),2)
        self.assertTrue(c.byAlias['M0112'].row['is_new'])
        self.assertTrue(c.byAlias['M0113'].row['is_new'])
        self.assertFalse(c.byAlias['M0114'].row['is_new'])
        self.assertTrue(any('😀' in str(f.row.get('request_memo','')) for f in c.rows))
        self.assertEqual(sum(a['alias'].startswith('qa-wd') and a['consumable'] for a in c.accounts),5)
        self.assertEqual(sum(a['alias'].startswith('qa-pw') for a in c.accounts),5)
        self.assertEqual(len(c.signupEmails),20)

    def testProductionTargetAndNaiveTimeRejected(self):
        with self.assertRaises(ValueError):makeSql(self.cat,'coffeul_prod','dev')
        with self.assertRaises(ValueError):makeSql(self.cat,'coffeul;DROP','dev')
        with self.assertRaises(ValueError):parseTime('2026-11-25T10:00:00')

    def testResetsAreScopedAndDoNotDisableConstraints(self):
        sql=makeSql(self.cat,'coffeul','test','reset_consumables')
        self.assertNotIn('FOREIGN_KEY_CHECKS',sql)
        self.assertNotIn('TRUNCATE',sql)
        self.assertIn('ormo_qa_seed_registry',sql)
        self.assertIn('NATURAL_KEY_POINTS_TO_ANOTHER_ID_menu_item'.encode().hex(),sql)
        self.assertIn('START TRANSACTION;',sql)
        self.assertIn('COMMIT;',sql)

    def testVerificationOnlyAcceptsDeclaredTestMailboxes(self):
        with self.assertRaises(ValueError):prepareVerificationSql(self.cat,'coffeul','test','somebody@g.eulji.ac.kr','SIGNUP','valid')
        with self.assertRaises(ValueError):advanceSql(self.cat,'coffeul','test',1,'REFUNDED',self.now)

    def testBundleContainsOfflineManifestAndChecks(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory);writeBundle(self.cat,p,'coffeul','test')
            self.assertTrue((p/'reset_consumables.sql').exists())
            self.assertEqual(len(json.loads((p/'image-urls.json').read_text())),32)

    def testReorderAndDedicatedMenuIsolation(self):
        c=self.cat
        self.assertEqual([r['alias'] for r in c.reorderCases],['R1','R2','R3','R4','R5','R6','R7','R8'])
        changed={entityId for (table,entityId) in c.beforeReorder if table=='menu_item'}
        self.assertTrue(all(not changed.intersection(item['menuId'] for item in o['reorderItems']) for o in c.orders if o['account']!='qa-reorder'))
        r7=next(o for o in c.orders if o['alias']=='R7')
        self.assertEqual(r7['lines'],20)
        self.assertEqual(r7['reorderItems'][0]['quantity'],20)
        self.assertEqual(c.byAlias['qa-school-c'].row['id'],BASE+4036)
        self.assertEqual(next(o['orderId'] for o in c.orders if o['alias']=='popular-5'),BASE+300288)

    def testOpenHistoryAndMultistoreCounts(self):
        c=self.cat
        self.assertEqual(sum(o['account']=='qa-heavy' and o['storeAlias']=='S01' for o in c.orders),80)
        self.assertEqual(sum(o['account']=='qa-heavy' and o['storeAlias']=='S02' for o in c.orders),70)
        self.assertEqual({o['lines'] for o in c.orders if o['account']=='qa-heavy' and o['storeAlias']=='S01'},{1,2,10,20})
        for account in ['qa-page20','qa-page21','qa-filtered','qa-wd-block']:
            self.assertTrue(all(o['storeAlias']=='S01' for o in c.orders if o['account']==account))
        self.assertEqual(Counter(o['storeAlias'] for o in c.orders if o['account']=='qa-multistore'),{'S01':2,'S08':2,'S02':1})
        for account in ['qa-longname-ko','qa-longname-en']:self.assertEqual(len(c.byAlias[account].row['name']),20)

    def testHeavyOpenHistoryUsesOnlyCurrentlyOrderableItems(self):
        c=self.cat;menus={f.row['id']:f.row for f in c.rows if f.table=='menu_item'};options={f.row['id']:f.row for f in c.rows if f.table=='option_item'}
        for o in c.orders:
            if o['account']!='qa-heavy' or o['storeAlias']!='S01':continue
            for item in o['reorderItems']:
                self.assertFalse(menus[item['menuId']]['is_sold_out'])
                self.assertEqual(menus[item['menuId']]['status'],'ACTIVE')
                self.assertTrue(all(not options[i]['is_sold_out'] for i in item['optionItemIds']))

    def testMenuMutationsHappenAfterOrderRowsInSql(self):
        sql=makeSql(self.cat,'coffeul','test')
        orderMarker=sql.index('INSERT INTO `orders`')
        after=sql.rfind('INSERT INTO `menu_item`')
        self.assertGreater(after,orderMarker)
        self.assertIn('DELETE FROM ormo_qa_seed_registry',sql)

    def testUnconfirmedDatabaseCannotExecute(self):
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as directory:
            result=subprocess.run([sys.executable,str(Path(__file__).with_name('qa_seed.py')),'seed_all','--database','DB_NAME_REQUIRED','--environment','test','--output-dir',directory,'--execute','--confirm-isolated-target','--mysql-login-path','never-connect'],capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('실제 격리 DB 이름이 확인되지 않은',result.stderr)


if __name__=='__main__':unittest.main()
