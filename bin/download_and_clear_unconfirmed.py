from uuid import UUID
import datetime as pydt
from pymongo import MongoClient
from uuid import UUID
import sys

def selSectionsQuery(uuid):
    startDate = pydt.datetime(2015,1,2)
    endDate = pydt.datetime.now()

    uuidQuery = {'user_id': uuid}
    startQuery = {'section_start_datetime': {'$gt': startDate}}
    endQuery = {'section_start_datetime': {'$lt': endDate}}

    return {'$and': [uuidQuery, startQuery, endQuery]}

def selTripsQuery(uuid):
    startDate = pydt.datetime(2015,1,2)
    endDate = pydt.datetime.now()

    uuidQuery = {'user_id': uuid}
    startQuery = {'trip_start_datetime': {'$gt': startDate}}
    endQuery = {'trip_start_datetime': {'$lt': endDate}}

    return {'$and': [uuidQuery, startQuery, endQuery]}

def delBadCollectedSections(uuid):
    sectionDb = MongoClient().Stage_database.Stage_Sections
    sectionDb.remove(selSectionsQuery(uuid))

def delBadCollectedTrips(uuid):
    tripDb = MongoClient().Stage_database.Stage_Trips
    tripDb.remove(selTripsQuery(uuid))

if __name__ == '__main__':
    if (len(sys.argv)) != 2:
        print """USAGE: selTripsQuery <uuid>"""
        exit(1)

    uuid = UUID(sys.argv[1])
    delBadCollectedSections(uuid)
    delBadCollectedTrips(uuid)
