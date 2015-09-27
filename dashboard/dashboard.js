
var fakedata = [1, 2, 3, 'testing']

var Dashboard = React.createClass({
	queryDatabase: function(dataset, header, values) {
		var base_url = this.props.base_url;

		$.ajax({
			url: base_url + '/' + dataset + '/' + header + '/' + values,
			dataType: 'json',
			success: function(response_data) {
				console.log(response_data);
				var transformedData = response_data.map(function(data_entry) {
					var date = data_entry[0];
					var count = data_entry[1];
					return [new Date(date), count];
				});
				this.setState({data: transformedData, dataset: dataset, header: header})
			}.bind(this),
			error: function(xhr, status, error) {
				console.log(status, error.toString());
			}.bind(this)
		})
	},

	getInitialState: function() {
		return {data: [], dataset: '', header: '', values: ''};
	},

	componentDidMount: function() {
		this.queryDatabase();
		//setInterval(this.queryDatabase, this.props.pollInterval);
	},

	render: function() {
		return (
			<div className="dashboard">
				<div className="queryEngine">
					<h2>Query Parameters</h2>
					<QueryEngine onQuerySubmit={this.queryDatabase} />
				</div>
				<GraphRenderer data={this.state.data} dataset={this.state.dataset} header={this.state.header} values={this.state.values} />
			</div>
		);
	}
})

var QueryEngine = React.createClass({
	// props:
	// handleQuery, function for dealing with a query, comes from dashboard class
	submitQuery: function() {
		var dataset = React.findDOMNode(this.refs.dataset).value.trim();
		var header = React.findDOMNode(this.refs.header).value.trim();
		var values = React.findDOMNode(this.refs.values).value.trim();
		console.log(dataset, header, values);
		this.props.onQuerySubmit(dataset, header, values);
	},

	render: function() {
		return (
			<div id="query">
				<label>Dataset: </label><input type="text" ref="dataset" />
				<label>Header: </label><input type="text" ref="header" />
				<label>Values: </label><input type="text" ref="values" />
				<span className="submit" onClick={this.submitQuery}>Submit Query</span>
			</div>
		);
	}
})

var GraphRenderer = React.createClass({
	// props:
	// data, data for viz in json format
	// type, type of visualization

	loadVisualizationPackage: function() {
		// not sure if needed here?
		var that = this;
		google.load('visualization', '1.0', {
			packages:['corechart'],
			callback: that.drawCharts
		});		
	},

	drawCharts: function() {
		if (!google.visualization) {
			this.loadVisualizationPackage();
			return;		
		}
		var data = this.props.data;
		var dataTable = new google.visualization.DataTable();
		dataTable.addColumn('date', 'Date');
		dataTable.addColumn('number', 'Count')

        dataTable.addRows(data);

        // Set chart options
        var options = {'title':'Counts of ' + this.props.header + "=" + this.props.values + " in " + this.props.dataset + ' over time.' ,
                       'width':800,
                       'height':500};

        // Instantiate and draw our chart, passing in some options.
        var chart = new google.visualization.LineChart(React.findDOMNode(this));
        chart.draw(dataTable, options);
  
  	},

	componentDidMount: function() {
		this.drawCharts();
	},

	componentDidUpdate: function() {
		this.drawCharts();
	},

	render: function() {
		return (
			<div id="graph"></div>
		);

	}
})

React.render(
  <Dashboard pollInterval={1000} base_url='/query' />,
  document.getElementById('content')
);