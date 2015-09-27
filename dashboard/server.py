from bottle import route, run, static_file
import json
from query_engine import StatsQueryEngine
import os

@route('/query/<dataset>/<header>/<val>')
def load_stat(dataset, header, val):
	if dataset == '' or not os.path.exists(dataset):
		dataset = 'client_stats_17_dec.csv'
	values = val.split(',')
	values = set([v.strip() for v in values])
	try:
		engine = StatsQueryEngine(dataset, datetime_header='client_ts')
		#print(engine.query_counts(header))
		return json.dumps(engine.query_counts(header, values=values))
	except Exception as e:
		return None
	
@route('/')
def index():
	return static_file('index.html', '')

@route('/<filename:re:.*\.js>')
def send_js(filename):
	print("here")
	return static_file(filename, '')

run(host='localhost', port=8080, debug=True)