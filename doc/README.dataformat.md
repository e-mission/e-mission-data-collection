# Data formats

----
## Introduction

The current plan is to have a small “phone sync” cache that is used for most bi-directional communication between the phone and the server. This will allow us to support offline operation, customize background operation, and reduce user perceived latency.

![][dataformat.architecture]

We will then transfer this data to the long term storage/analysis cache. The data formats in the two caches need not be the same, and in fact, we expect them to be significantly different. In particular, the data in the cache is optimized for flexibility, while the long-term storage is optimized for ease of analysis.

----
## User cache data format design

Here, we decide the data format for the user cache (flow 1 above). The flow is bi-directional, and so can again be split into phone -> server and server -> phone components. For now, we will endeavor to separate the data in the two components so that there is no overlap. This will help us in resolving conflicts because there will be none. We will revisit and extend this design if and when we find a data element that needs to be modified from both the server and the phone sides.

### Phone -> server component

This can again be split into two components - user generated data and data from background sensing.

#### User generated data

We want this to be as flexible as possible in order to support the rapid deployment of screens that can collect additional data. Each screen can add new keys to this area, and the corresponding server side module can read the corresponding keys, e.g.


    {
      'confirmed_sections':
        {section_id_1: confirmed1,
         section_id_2: confirmed2,
         section_id_3: confirmed3}
    }


Since our screens are javascript/HTML based, this allows us to quickly deploy new functionality without requiring native code changes and an update to the app. We can simply create a new javascript screen that writes to this data, and deploy a new python module that reads from it.

#### Data from background sensing

This requires less flexibility since we have to write native code or deploy native code plugins to collect data. Since we can only get background data collection on iOS when we receive location updates, we will always collect location updates.

    {
        "locations": [
            location_obj1,
            location_obj2,
            location_obj3,
            ...],
        "activities": [
            activity_obj1,
            activity_obj2,
            activity_obj3,
            ...],
        "accelerometer": [
            accelerometer_obj1,
            accelerometer_obj2,
            accelerometer_obj3,
            ...],
        "fuel_consumption": [
            fuel_consumption_obj1,
            fuel_consumption_obj2,
            fuel_consumption_obj3,
            ...]
        ...
    }

Some other observations and requirements on the data from background sensing:

Some other requirements on the data from background sensing:

1. The `location` array is currently required, since background data collection on iOS is driven by location updates.
2. The format of the objects can be platform specific -- we will have a conversion layer on the server to transform data into the common long-term data storage format. This allows us to have a much more flexible conversion step that can be tweaked as necessary without updating the client. In particular, we expect that both the location and activity objects will be different on android and iOS.
3. We expect that all data objects will have at least an associated timestamp.
4. The JSON objects will be inserted into SQLite databases on android and iOS. Since the location points are used by the data collection state machine, each row will contain some extracted information for easy querying. It will also contain the raw JSON for easy syncing!

### Combined

The two will be combined into a single data structure as follows:

    { 'user': 
      {'confirmed_sections': 
            {section_id_1: confirmed1,
             section_id_2: confirmed2,
             section_id_3: confirmed3}
      },
      'background':
      {
        "locations": [
            location_obj1,
            location_obj2,
            location_obj3,
            ...],
        "activities": [
            activity_obj1,
            activity_obj2,
            activity_obj3,
            ...],
        "accelerometer": [
            accelerometer_obj1,
            accelerometer_obj2,
            accelerometer_obj3,
            ...],
        "fuel_consumption": [
            fuel_consumption_obj1,
            fuel_consumption_obj2,
            fuel_consumption_obj3,
            ...]
        ...
      }
    }

### Server -> phone component

This can again be divided into two components. The first is intended for user display (results, etc), and the second is intended for configuring the background collection. Again, the structure of the first part needs to be very flexible, in order to support dynamically deploying screens along with dynamically generated data. At the same time, the second part does not need to be as flexible, since changes to it will not work unless there are changes to the corresponding native code.

#### User results

    {
        'data':
        {
            'carbon_footprint':
            {
                'mine': mFootprint,
                'mean': meanFootprint,
                'all_drive': driveFootprint,
                'optimal': optimalFootprint,
            }
            'distances':
            {
                'mode1': mode1Distance,
                 ....
            }
        ...
        }
        'game': 
        {
            'my_score': 3435,
            'other_scores':
            {
                'person1': 2313,
                'person2': 4123,
                'person3': 2111
            }
        }
        ...
    }


#### Background configuration

    {
        'pull_sensors': {
            ['accelerometer', 'gyroscope', ...]
        },
        'location':
        {
             'accuracy': POWER_BALANCED_ACCURACY,
             'filter': DISTANCE_FILTER,
             'geofence_radius': 100,
             ...
        },
        ...
    }

#### Common results

Things that can be used for both user results and background configuration, e.g. common trips.

#### Combined
    {
        'user':
        {
            'data':
            {
                'carbon_footprint':
                {
                    'mine': mFootprint,
                    'mean': meanFootprint,
                    'all_drive': driveFootprint,
                    'optimal': optimalFootprint,
                }
                'distances':
                {
                    'mode1': mode1Distance,
                     ....
                }
            ...
            }
            'game': 
            {
                'my_score': 3435,
                'other_scores':
                {
                    'person1': 2313,
                    'person2': 4123,
                    'person3': 2111
                }
            }
            ...
        },
        'background_config':
        {
            'pull_sensors': {
                ['accelerometer', 'gyroscope', ...]
            },
            'location':
            {
                 'accuracy': POWER_BALANCED_ACCURACY,
                 'filter': DISTANCE_FILTER,
                 'geofence_radius': 100,
                 ...
            },
            ...
        },
        'common':
        {
            'tour_models': {
                ...
            }
        }
    }


### Implementation plan
Since this is the background data collection repo, we will implement the
background parts of both the server -> phone and phone -> server components as
part of enhancing the background data collection.

[dataformat.architecture]: http://www.cs.berkeley.edu/~shankari/figs/dataformat.architecture.png
