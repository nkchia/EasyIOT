package smarthome
import org.grails.web.json.*

//Endpoint that provides fulfillment to Google SmartHome Intents
//Uri: /request
class RequestController {
    def index() {
    	JSONObject requestObj = request.JSON
    	String type = requestObj.getJSONArray("inputs").getJSONObject(0).getString("intent")
    	    	
    	if (type == "action.devices.DISCONNECT") {
    		render "{}"
		} else {
			String token = request.getHeader("Authorization")
			token = token.substring(7)
        	StateManager tracker = new StateManager(requestObj.getString("requestId"), token)
        	if (type == "action.devices.EXECUTE") {
    			render tracker.executeFormat(requestObj)
    		} else {
				if (type == "action.devices.SYNC") {
       				render tracker.syncFormat()
       			} else {
     				render tracker.queryFormat(requestObj)
       			}
		  	}
		}
		
    }
}


