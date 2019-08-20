package smarthome

import org.apache.tomcat.jni.Thread
import org.grails.web.json.*

//Sends and retrieves data to a server using Tcp protocols
class TcpClient {

    private Socket socket
    private DataOutputStream dOut
    private DataInputStream dIn

    TcpClient() {
        this.socket = null
        this.dOut = null
        this.dIn = null
    }

    void connect(String hostname, int port) {
        this.socket = new Socket(hostname, port)
        this.dOut = new DataOutputStream(socket.getOutputStream())
        this.dIn = new DataInputStream(socket.getInputStream())
    }

    void disconnect()
    {
        try {
            if (socket != null)
                socket.close()
        } catch (IOException e) {
            e.printStackTrace()
        }

        try {
            dOut.close()
        } catch (IOException e) {
            e.printStackTrace()
        }

        try {
            dIn.close()
        } catch (IOException e) {
            e.printStackTrace()
        }
        dIn = null
        dOut = null
        socket = null
    }

    //@param String to be sent to server
    //@return JSONObject of response
    JSONObject sendAndReceiveMessage(String message) {
        try {
            byte[] data = message.getBytes()
            dOut.write(data)

            while (dIn.available() <= 0) {
                Thread.sleep(500)
            }

            int count = dIn.available()
            byte[] packetData = new byte[count]
            dIn.readFully(packetData)
            return new JSONObject(new String(packetData))
        } catch (IOException e) {
            e.printStackTrace()
            return null
        }
    }
}


