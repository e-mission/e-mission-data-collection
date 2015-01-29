import unittest
from pymongo import MongoClient
from uuid import UUID
import bin.download_and_clear_unconfirmed as testcode
import datetime as pydt

class TestClearTrips(unittest.TestCase):
    def setUp(self):
        self.sectionDb = MongoClient().Stage_database.Stage_Sections
        self.sectionDb.remove({'user_id': 'test_user'})

        self.tripDb = MongoClient().Stage_database.Stage_Sections
        self.tripDb.remove({'user_id': 'test_user'})

    def testClearSection(self):
        self.sectionDb.insert({'user_id': 'test_user', 'section_start_datetime': pydt.datetime(2014,12,15)})
        self.sectionDb.insert({'user_id': 'test_user', 'section_start_datetime': pydt.datetime(2015,1,6)})
        self.assertEqual(self.sectionDb.find({'user_id': 'test_user'}).count(), 2)
        self.assertEqual(self.sectionDb.find(testcode.selSectionsQuery('test_user')).count(), 1)
        testcode.delBadCollectedSections('test_user')
        self.assertEqual(self.sectionDb.find({'user_id': 'test_user'}).count(), 1)

    def testClearTripQuery(self):
        self.tripDb.insert({'user_id': 'test_user', 'trip_start_datetime': pydt.datetime(2014,12,15)})
        self.tripDb.insert({'user_id': 'test_user', 'trip_start_datetime': pydt.datetime(2015,1,6)})
        self.assertEqual(self.tripDb.find({'user_id': 'test_user'}).count(), 2)
        self.assertEqual(self.tripDb.find(testcode.selTripsQuery('test_user')).count(), 1)

if __name__ == '__main__':
    unittest.main()
