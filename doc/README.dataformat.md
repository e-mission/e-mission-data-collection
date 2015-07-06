# Data formats

----
## Introduction

The current plan is to have a small “phone sync” cache that is used for most bi-directional communication between the phone and the server. This will allow us to support offline operation, customize background operation, and reduce user perceived latency.

![][dataformat.architecture]

We will then transfer this data to the long term storage/analysis cache. The data formats in the two caches need not be the same, and in fact, we expect them to be significantly different. In particular, the data in the cache is optimized for flexibility, while the long-term storage is optimized for ease of analysis.

----
## User cache data format design

Here, we decide the data format for the user cache (flow 1 above). The flow is bi-directional, and so can again be split into phone -> server and server -> phone components. For now, we will endeavor to separate the data in the two components so that there is no overlap. This will help us in resolving conflicts because there will be none. We will revisit and extend this design if and when we find a data element that needs to be modified from both the server and the phone sides.

Further details can be found in the wiki:
https://github.com/e-mission/e-mission-data-collection/wiki/Data-format-design

[dataformat.architecture]: http://www.cs.berkeley.edu/~shankari/figs/dataformat.architecture.png
