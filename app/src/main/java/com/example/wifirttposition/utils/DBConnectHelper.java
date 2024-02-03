package com.example.wifirttposition.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 作者　: mmh
 * 时间　: 2023/2/4
 * 描述　: 链接打开数据库
 */
public class DBConnectHelper {
    private static String diver = "com.mysql.jdbc.Driver";
    //utf-8 方便向数据库中写入中文 不出现乱码
    private static String url ="jdbc:mysql://10.16.104.102:3306/indoor_location?characterEncoding=utf-8";
    private static String user = "root";//用户名
    private static String password = "mmh20011004";//密码


    /*
    * 链接数据库
    * */
    public static Connection getConn(){
        Connection connection = null;
        try {
            Class.forName(diver);
            //获取连接
            connection = (Connection) DriverManager.getConnection(url,user,password);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }
}
