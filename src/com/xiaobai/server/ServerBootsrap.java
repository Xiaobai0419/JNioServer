package com.xiaobai.server;

import java.io.IOException;

/**  
* 创建时间：2018年7月18日 上午9:34:51  
* 项目名称：NioServer  
* @author Daniel Bai 
* @version 1.0   
* @since JDK 1.6.0_21  
* 文件名称：ServerBootsrap.java  
* 类说明：  
*/
public class ServerBootsrap {
	
	public void start(int port) {
		NioServer nioServer = NioServer.getInstance();
		try {
			nioServer.build(port);
			new Thread(nioServer).start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		new ServerBootsrap().start(8888);
	}
}
