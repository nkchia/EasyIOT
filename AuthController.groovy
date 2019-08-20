package smarthome
import org.grails.web.json.*

//Endpoint for authentication for Google SmartHome
//Uri: /auth
class AuthController {
    final String HOSTNAME = "app.ez1.cloud"
    final int PORT = 32788

    def index() {
        if (params.client_id == "iot" &&
                params.redirect_uri == "https://oauth-redirect.googleusercontent.com/r/easyiot-92840") {
            TcpClient client = new TcpClient()
            client.connect(HOSTNAME,PORT)
            //FUTURE IMPLEMENTATION Redirect to login page
            JSONObject response = client.sendAndReceiveMessage("{'action': 'login', 'data': 'user', " +
                    "'request_param':{'username':'hidden','password':'hidden'}}")
            String token = response.getJSONObject("response").getString("access_token")
            String url = ("https://oauth-redirect.googleusercontent.com/r/easyiot-92840#access_token=" +
                    token + "&token_type=bearer&state=" + params.state + "&response_type=token")
            redirect(url: url)
        }
    }
}
