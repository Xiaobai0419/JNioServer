package com.xiaobai.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**  
* 创建时间：2018年7月18日 上午9:25:07  
* 项目名称：NioServer  
* @author Daniel Bai 
* @version 1.0   
* @since JDK 1.6.0_21  
* 文件名称：BioClient.java  
* 类说明：  
*/
public class BioClient {
	
	
	public static void main(String[] args) {
		Socket socket = null;
		InputStream in = null;
		OutputStream out = null;
		try {
			socket = new Socket("10.10.92.3",8888);
			out = socket.getOutputStream();
			out.write("QUERY_TIME".getBytes("UTF-8"));
			out.flush();
			in = socket.getInputStream();
			byte[] bytes = new byte[2048];
			int len;
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			while((len = in.read(bytes)) > 0) {
				bout.write(bytes, 0, len);
			}
			bout.flush();
			String response = bout.toString("UTF-8");
			System.out.println("Client Receive:" + response);
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
}
