from datetime import datetime, timedelta
from util import load_data, generate_key_from_datetime, generate_datetime_from_key

class StatsQueryEngine:
	def __init__(self, data_source, datetime_header=''):
		self.data = load_data(data_source, datetime_header)
		self.datetime_header = datetime_header

	def query_column(self, header):
		return self.data[header]

	def get_unique_vals(self, header):
		unique_vals = set()
		column = self.query_column(header)
		for elem in column:
			if elem not in unique_vals:
				unique_vals.add(elem)
		return unique_vals

	def get_min(self, header):
		return self.data[header].min()

	def get_max(self, header):
		return self.data[header].max()

	def query_counts(self, header, values=None, interval=timedelta(days=1)):
		col_min = self.get_min(self.datetime_header)
		col_max = self.get_max(self.datetime_header)
		datetime_column = self.query_column(self.datetime_header)
		column = self.query_column(header)

		counts = {}
		counts[generate_key_from_datetime(col_min)] = 0
		c = 0
		while c<len(column) and col_min <= col_max:
			year, month, day = col_min.year, col_min.month, col_min.day 
			year_equiv = datetime_column[c].year == col_min.year
			month_equiv = datetime_column[c].month == col_min.month
			day_equiv = datetime_column[c].day == col_min.day
			while c<len(column) and year_equiv and month_equiv and day_equiv:
				# either values is undefined, and you increment the count no matter what
				# or the values is defined,and the current row val matches one of the vals in
				# the values array
				if not values or column[c] in values:
					counts[generate_key_from_datetime(col_min)] += 1
				c += 1
				year_equiv = datetime_column[c].year == col_min.year
				month_equiv = datetime_column[c].month == col_min.month
				day_equiv = datetime_column[c].day == col_min.day
			
			col_min += interval
			
			if generate_key_from_datetime(col_min) not in counts:
				counts[generate_key_from_datetime(col_min)] = 0

		return sorted(counts.items(), key=lambda x: generate_datetime_from_key(x[0]))


def tests():
	x = StatsQueryEngine('client_stats_17_dec.csv', 'client_ts')
	ts_header = 'client_ts'
	#print "Days of dataset range from " + str(datetime.fromtimestamp(x.get_min(ts_header))).split(' ')[0] + " to " + str(datetime.fromtimestamp(x.get_max(ts_header))).split(' ')[0] + "."
	#print(x.get_min(ts_header))
	#print(x.get_max(ts_header))
	counts = x.query_counts('stat', values=set(['battery_level']))
	#counts = [{'date': c[0], 'value': c[1]} for c in counts]
	print(counts)

#tests()

