/*
Copyright 2016 Load Impact

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import groovy.json.JsonSlurperClassic 

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

/* Load Impact test Id */
def testId = YOUR_TEST_ID_GOES_HERE
/* API KEY in format user:pass and pass is blank, remember the : */
def API_KEY = 'YOUR_API_KEY_GOES_HERE:'

def encoded = API_KEY.bytes.encodeBase64().toString()

stage "Kickoff performance test"

def response = httpRequest httpMode: 'POST', requestBody: "", customHeaders: [[name: 'Authorization', value: 'Basic ' + encoded]], url: 'https://api.loadimpact.com/v2/test-configs/' + testId + '/start'

/* status = 201 is expected */
if (response.status != 201) {
  exit ("Could not start test " + testId + ": " + response.status + "\n" + response.content)
}

def jid = jsonParse(response.content)
def tid = jid["id"]

timeout (time:5, unit: "MINUTES")
{
  waitUntil {
    /* waitUntil needs to slow down */
    sleep (time: 10, unit: "SECONDS")
    
    def r = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: 'Basic ' + encoded]], url: 'https://api.loadimpact.com/v2/tests/'+ tid + '/'
    def j = jsonParse(r.content)
    echo "status: " + j["status_text"]
    return (j["status_text"] == "Running");
  }    
}

/* content is something like {"id":3715298}
   where id is the started test run id
*/

stage "Performance test running"

/*
get and tell percentage completed
*/
maxVULoadTime = 0.0
sVUL = 0
valu = 0.0
waitUntil {
  /* No need to get state of test run as often */
  sleep (time: 30, unit: "SECONDS")

  /* Get percent completed */    
  def r = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: 'Basic ' + encoded]], url: 'https://api.loadimpact.com/v2/tests/' + tid + '/results?ids=__li_progress_percent_total'
  def j = jsonParse(r.content)
  def size = j["__li_progress_percent_total"].size()
  def last = j["__li_progress_percent_total"]
  echo "percentage completed: " + last[size - 1]["value"]

  /* Get vu load time */
  r = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: 'Basic ' + encoded]], url: 'https://api.loadimpact.com/v2/tests/' + tid + '/results?ids=__li_user_load_time'
  j = jsonParse(r.content)

  sVUL = j["__li_user_load_time"].size()
  
  if (sVUL > 0) {
    echo "last: " + j["__li_user_load_time"][sVUL - 1]["value"]
      /* set max vu load time */
    valu = j["__li_user_load_time"][sVUL - 1]["value"]
    if (valu > maxVULoadTime) {
      maxVULoadTime = valu
    }

    /* check if VU Load Time > 1000 msec */
    /* It will fail the build */
    if (maxVULoadTime > 1000) {
     exit ("VU Load Time extended limit of 1 sec: " + maxVULoadTime)
    }
  }

  return (last[size - 1]["value"] == 100);
}

stage "Show results"
echo "Max VU Load Time: " + maxVULoadTime
echo "Full results at https://app.loadimpact.com/test-runs/" + tid

