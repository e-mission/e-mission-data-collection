import numpy as np
from pandas import *
import glob
from os import path
from datetime import datetime

def load_data(data_source, datetime_header):
	# Takes data, loads into pandas frame
	f = open(data_source, 'r')
	raw_data = f.readlines()
	f.close()

	#return DataFrame(raw_data)
	headers, data = raw_data[0].strip().split(','), raw_data[1:]
	header_to_indx = {}
	indx_to_header = {}
	for indx, header in enumerate(headers):
		header_to_indx[header] = indx
		indx_to_header[indx] = header
	datetime_index = header_to_indx[datetime_header]


	data = [d.strip().split(',') for d in data]
	data = clean_data(data, datetime_index)
	x = DataFrame(data, columns=headers)


	return x

def clean_data(data, datetime_index):
	clean_data = []
	failed = 0
	for row in data:
		cleaned_row = clean_row(row, datetime_index)
		if cleaned_row:
			clean_data.append(cleaned_row)
		else:
			failed += 1
	print("Failed to process " + str(failed) + " rows.")
	return clean_data

def clean_row(row, datetime_index):
	clean_row = []
	for indx, r in enumerate(row):
		if indx == datetime_index:
			try: 
				clean_row.append(timestamp_to_datetime(r))
			except Exception as e:
				#clean_row.append(r)
				return None
		else:
			try: 
				clean_row.append(float(r))
			except Exception as e:
				clean_row.append(r)
				#return None
	return clean_row

def timestamp_to_datetime(timestamp):
	if type(timestamp) == str:
		if len(timestamp) < 10:
			#print(timestamp)
			raise Exception
		if len(timestamp) <= 10:
			return datetime.fromtimestamp(float(timestamp))
		else:
			return datetime.fromtimestamp(float(timestamp[0:10]))
	else:
		pass
		print("Timestamp was not type 'str', this case has not been implemented yet.")
		#return datetime.fromtimestamp(float(str(int(timestamp))[0:10]))


def generate_key_from_datetime(dateobj):
	return str(dateobj.year) + "-" + str(dateobj.month) + "-" + str(dateobj.day)

def generate_datetime_from_key(key):
	year, month, day = [int(k) for k in key.split('-')]
	return datetime(year=year, month=month, day=day)


def unittest1():
	#data, header_to_indx = load_data('client_stats_17_dec.csv')
	x = load_data('client_stats_17_dec.csv', datetime_header='client_ts')
	#x['client_ts'].apply(timestamp_to_datetime)

	headers = 'reported_ts,stat,reading,client_ts,client_os_version,client_app_version,user,_id'
	for h in headers.split(','):
		print h, x[h][30000]

def unittest2():
	x = load_data('result_stats_17_dec.csv', datetime_headers=['ts'])
	#x['client_ts'].apply(timestamp_to_datetime)

	headers = 'stat,_id,reading,ts,user'
	for h in headers.split(','):
		print h, x[h][5100]

def unittest3():
	x = load_data('server_stats_17_dec.csv', datetime_headers=['client_ts'])
	#x['client_ts'].apply(timestamp_to_datetime)

	headers = 'stat,_id,reading,client_ts,user'
	for h in headers.split(','):
		print h, x[h][5100]	

#unittest1()
