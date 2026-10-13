import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
from fastapi.testclient import TestClient
from price_reference import conformal_radius, PriceReference, request_features, support_metadata
import pandas as pd

class ConstantModel:
    def predict(self, frame):
        return np.full(len(frame), 20000.)

class ReferenceTests(unittest.TestCase):
    def setUp(self):
        self.request = dict(schema_version='1.0',request_id='test',city='杭州',district='滨江',community_name='测试小区',business_area='测试板块',building_area_sqm=80,rooms=2,halls=1,bathrooms=1,total_floors=12,floor_level='MIDDLE',orientation=['S'],decoration='FINE',has_elevator=True,property_usage='RESIDENTIAL')
        frame = pd.DataFrame([request_features(self.request)]*20)
        self.service = PriceReference(dict(pipeline=ConstantModel(),support=support_metadata(frame),radii={'seen':.2},calibration_counts={'seen':100},coverage=.9,model_version='test',data_version='test',listing_date_min='2020-01-01',listing_date_max='2021-01-01'))

    def test_finite_quantile(self):
        self.assertEqual(conformal_radius(np.arange(10)),9)
        with self.assertRaises(ValueError):
            conformal_radius([1,2])

    def test_interval_contains_point_and_units(self):
        data = self.service.predict(self.request)['data']
        self.assertEqual(data['status'],'DEMO_ONLY')
        self.assertEqual(data['estimated_total_price_yuan'],1600000)
        self.assertLess(data['price_interval']['lower_total_price_yuan'],1600000)
        self.assertGreater(data['price_interval']['upper_total_price_yuan'],1600000)

    def test_unavailable_nulls(self):
        for change in ({'city':'上海'},{'property_usage':'GARAGE'},{'building_area_sqm':10000},{'community_name':'陌生小区','business_area':None}):
            data=self.service.predict(self.request|change)['data']
            self.assertEqual(data['status'],'UNAVAILABLE')
            self.assertIsNone(data['estimated_total_price_yuan'])
            self.assertIsNone(data['price_interval'])

    def test_missing_calibration(self):
        self.service.bundle['radii']={}
        self.assertEqual(self.service.predict(self.request)['data']['status'],'UNAVAILABLE')

    def test_invalid_cross_fields(self):
        for change in ({'orientation':['UNKNOWN','S']},{'longitude':120},{'internal_area_sqm':100}):
            with self.assertRaises(ValueError):
                self.service.predict(self.request|change)

    def test_api(self):
        from api import create_app
        client=TestClient(create_app(self.service))
        self.assertEqual(client.post('/ai/predict/price',json=self.request).status_code,200)
        self.assertEqual(client.post('/ai/predict/price',json=self.request|{'rooms':0}).status_code,422)
        client.app.state.service=None
        self.assertEqual(client.get('/ready').status_code,503)
        self.assertEqual(client.post('/ai/predict/price',json=self.request).status_code,503)

if __name__=='__main__':
    unittest.main()
