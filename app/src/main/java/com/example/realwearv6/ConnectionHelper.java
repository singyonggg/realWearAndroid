package com.example.realwearv6;

import android.os.StrictMode;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionHelper {
    Connection con;
    String roomName, ip, database, port;

    public Connection connectionclass(){
        ip="192.168.1.50";
        database="rwDb";
        port="8554";

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Connection connection=null;
        String ConnectionURL=null;

        try{
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            ConnectionURL= "jdbc:jtds:sqlserver://"+ ip + ":"+ port+";"+ "databasename="+ database+";";
            connection= DriverManager.getConnection(ConnectionURL);

        } catch (Exception ex) {
            Log.e("Error",ex.getMessage());
        }

        return connection;
    }

}
