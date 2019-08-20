package smarthome

import org.grails.web.json.*


//Returns the appropriate response based on the type of Google SmartHome Intent
class StateManager {
    final List<String> OUTLETS = ["PP001", "PP002", "PP003", "PP004",
                        "PP005", "PP006", "EZ000101", "EZ000301",
                        "GR000101", "GR000201", "GR000301", "GR000401"]
    final List<String> LIGHTS = ["CM110R1", "CM109R1", "CM110R102",
                       "CM110R103", "JX000101", "EZ000201", "EZ000401"]

    final String HOSTNAME = "app.ez1.cloud"
    final int PORT = 32788

    private String token //Authentication token given by SmartHome Intent
    private String requestId //Conversation ID by SmartHome Intent
    private Set<String> success //MacAddresses that successfully complete commands
    private Set<String> error //MacAddresses that failed during execution
    private TcpClient client //Client that sends TCP requests and listens to responses

    //@param requestId and authentication token issued by SmartHome Intent
    //@return StateManager
    StateManager(String requestId, String token){
        this.requestId = requestId
        this.token = token
        this.success = new HashSet<String>()
        this.error = new HashSet<String>()
        this.client = new TcpClient()
    }

    //@return JSONString for SYNC intent response
    String syncFormat() {
        JSONObject list = getList()

        JSONObject response = new JSONObject()
        JSONObject payload  = new JSONObject()
        JSONArray deviceInfo = new JSONArray()

        int length = list.getJSONArray("response").length()
        for (int i = 0; i < length; i++) {
            JSONObject toBeAdded = new JSONObject()
            JSONObject currentDevice = list.getJSONArray("response").getJSONObject(i)
            toBeAdded.put("id", currentDevice.getString("macAddress"))

            String model = currentDevice.getString("model")
            JSONObject data = new JSONObject()
            JSONArray functions = new JSONArray()

            //FUTURE IMPLEMENTATION
            //In the case that new types of devices are added, make sure to implement custom data
            //data.put("device", TYPE OF DEVICE)
            //This data is used during compileRequests below
            if (this.OUTLETS.contains(model)) {
                toBeAdded.put("type", "action.devices.types.OUTLET")
                data.put("device", "outlet")
            } else if (this.LIGHTS.contains(model)) {
                toBeAdded.put("type", "action.devices.types.LIGHT")
                data.put("device", "light")
            }

            //FUTURE IMPLEMENTATION
            //If more functions are to be added, the appropriate traits should be provided in queryFormat below
            functions.add("action.devices.traits.OnOff")
            toBeAdded.put("traits", functions)

            JSONObject name = new JSONObject()
            JSONArray defaultNames = new JSONArray()
            //FUTURE IMPLEMENTATION - Populate defaultNames
            name.put("defaultNames", defaultNames)
            String customName = currentDevice.getString("deviceName")
            name.put("name", customName)
            toBeAdded.put("name", name)

            //FUTURE IMPLEMENTATION - Report State https://developers.google.com/actions/smarthome/report-state
            toBeAdded.put("willReportState", false)

            toBeAdded.put("customData", data)

            deviceInfo.add(toBeAdded)
        }

        payload.put("devices", deviceInfo)
        response.put("requestId", this.requestId)
        response.put("payload", payload)
        String reply = response.toString()
        return reply
    }

    //@param JSONObject of SmartHome Intent
    //@return JSONString for QUERY intent response
    String queryFormat(JSONObject requestObj) {
        JSONObject response = new JSONObject()
        JSONObject payload = new JSONObject()
        JSONObject deviceInfo = new JSONObject()

        JSONArray concernedDevices = requestObj.getJSONArray("inputs").getJSONObject(0).getJSONObject("payload").getJSONArray("devices")
        JSONObject list = getList()

        int length = concernedDevices.length()
        for (int i = 0; i < length; i++) {
            JSONObject state = new JSONObject()
            int listLength = list.getJSONArray("response").length()
            for (int j = 0; j < listLength; j++) {
                if (list.getJSONArray("response").getJSONObject(j).getString("macAddress") == concernedDevices.getJSONObject(i).getString("id")) {
                    /*
                        FUTURE IMPLEMENTATION  (once server is capable of telling whether a device is online or on/off)
                        As of now, devices are ALWAYS on and online
                        * NOTE More fields may be needed if more functions are implemented in SYNCFormat above

                        JSONObject current = list.getJSONArray("response").getJSONObject(j)
                        if (this.OUTLETS.contains(current.getString("model)) {
                            //Check if outlet is on/off
                        } else if (this.LIGHTS.contains(current.getString("model"))) {
                            if (current.getJSONObject("data").getInt(redLevel) == 0 &&
                                current.getJSONObject("data").getInt(blueLevel) == 0 &&
                                current.getJSONObject("data").getInt(greenLevel) == 0) {
                                state.put("on", false)
                            } else {
                                state.put("on", true)
                            }
                        }
                    */
                    state.put("on", true)
                    state.put("online", true)
                }
            }
            deviceInfo.put(concernedDevices.getJSONObject(i).getString("id"), state)
        }
        payload.put("devices", deviceInfo)
        response.put("requestId", this.requestId)
        response.put("payload", payload)
        String reply = response.toString()
        return reply
    }

    //@param JSONObject of SmartHome Intent
    //@return JSONString for EXECUTE intent response
    String executeFormat(JSONObject requestObj) {
        compileAndSubbmitRequests(requestObj)

        JSONObject response = new JSONObject()
        JSONObject payload = new JSONObject()
        JSONArray status = new JSONArray()

        if (this.success.size() > 0) {
            JSONObject toBeAdded = new JSONObject()
            JSONArray deviceIds = new JSONArray()

            for (String id : this.success) {
                deviceIds.add(id)
            }
            toBeAdded.put("ids", deviceIds)
            toBeAdded.put("status", "SUCCESS")
            status.add(toBeAdded)
        }

        if (this.error.size() > 0) {
            JSONObject toBeAdded = new JSONObject()
            JSONArray deviceIds = new JSONArray()

            for (String id : this.error) {
                deviceIds.add(id)
            }
            toBeAdded.put("ids", deviceIds)
            toBeAdded.put("status", "ERROR")
            status.add(toBeAdded)
        }

        payload.put("commands", status)
        response.put("requestId", this.requestId)
        response.put("payload", payload)
        String reply = response.toString()
        return reply
    }

    //Compiles a list of tcp requests of "action" "control"
    //@param JSONObject of SmartHome Intent
    //@return List<String> of tcp requests
    private void compileAndSubbmitRequests (JSONObject requestObj) {
        JSONArray commands = requestObj.getJSONArray("inputs").getJSONObject(0).getJSONObject("payload").getJSONArray("commands")
        List<String> requests = new ArrayList<String>()
        List<String> devices = new ArrayList<String>()
        for (int i = 0; i < commands.length(); i++) {
            for (int j = 0; j < commands.getJSONObject(i).getJSONArray("devices").length(); j++) {
                for (int k = 0; k < commands.getJSONObject(i).getJSONArray("execution").length(); k++) {
                    JSONObject currentDevice = commands.getJSONObject(i).getJSONArray("devices").getJSONObject(j)
                    JSONObject currentExecution = commands.getJSONObject(i).getJSONArray("execution").getJSONObject(k)
                    switch (currentExecution.getString("command")) {
                        //FUTURE IMPLEMENTATION
                        //Add more cases for different commands
                        case "action.devices.commands.OnOff" :
                            String currentId = currentDevice.getString("id")
                            if (currentDevice.getJSONObject("customData").getString("device").equals("light")) {
                                if (currentExecution.getJSONObject("params").getBoolean("on")) {
                                    requests.add("{'action':'control','data':'hue','request_param':{'macAddress':'" + currentId +
                                            "','whiteLevel':'50','hexValue':'#ffffff'}}")
                                } else {
                                    requests.add("{'action':'control','data':'hue','request_param':{'macAddress':'" + currentId +
                                            "','whiteLevel':'0','hexValue':'#000000'}}")
                                }
                            } else if (currentDevice.getJSONObject("customData").getString("device").equals("outlet")) {
                                if (currentExecution.getJSONObject("params").getBoolean("on")) {
                                    requests.add("{'action':'control','data':'smartplug','request_param':{'macAddress':'" + currentId +
                                            "','mainPower':'ON'}}")
                                } else {
                                    requests.add("{'action':'control','data':'smartplug','request_param':{'macAddress':'" + currentId +
                                            "','mainPower':'OFF'}}")
                                }
                            }
                            devices.add(currentId)
                    }
                }
            }
        }
        submitRequests(requests, devices)
    }

    //@return JSONObject of list of connected devices to account
    private JSONObject getList() {
        try {
            login()
            JSONObject response = this.client.sendAndReceiveMessage("{'action':'list','data':'devices'}")
            this.client.disconnect()
            return response
        } catch (UnknownHostException ex) {
            ex.printStackTrace()
            return null
        } catch (IOException ex) {
            ex.printStackTrace()
            return null
        }
    }

    //Sends a list of control requests to the server and keeps track of which devices succeed or fail
    //@param List<String> of requests that are of "action" "control", List<String> of MacAddresses with corresponding index
    private void submitRequests(List<String> requests, List<String> devices) {
        try {
            login()

            int length = requests.size()
            for (int i = 0; i < length; i++) {
                JSONObject response = this.client.sendAndReceiveMessage(requests.get(i))
                int status = response.getInt("code")
                String currentId = devices.get(i) //Corresponding MacAddress to current command
                if (status == 100 && !this.error.contains(currentId)) {
                    this.success.add(currentId)
                } else {
                    this.error.add(currentId)
                }
            }
            this.client.disconnect()
        } catch (UnknownHostException ex) {
            ex.printStackTrace()
        } catch (IOException ex) {
            ex.printStackTrace()
        }
    }

    //Login to server using authentication token
    //@return boolean true if successful login
    private boolean login(){
        try {
            this.client.connect(HOSTNAME, PORT)
            String command = "{'action':'login','data':'user','request_param':{'access_token':'" + this.token + "'}}"
            //String command = "{'action': 'login', 'data': 'user', 'request_param':{'username':'khgoh','password':'123456'}}"
            JSONObject response = this.client.sendAndReceiveMessage(command)

            return (response.getInt("code") == 100)
        } catch (UnknownHostException ex) {
            ex.printStackTrace()
            return false
        } catch (IOException ex) {
            ex.printStackTrace()
            return false
        }
    }
}
