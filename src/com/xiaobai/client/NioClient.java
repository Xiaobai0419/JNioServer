package com.xiaobai.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**  
* 创建时间：2018年7月18日 上午10:03:24  
* 项目名称：NioServer  
* @author Daniel Bai 
* @version 1.0   
* @since JDK 1.6.0_21  
* 文件名称：NioClient.java  
* 类说明：  
*/
public class NioClient {
	
	public static void main(String[] args) {
		try {
			int cnt = 0;
			Selector selector = Selector.open();
			SocketChannel socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(false);
			//如果连接成功，则直接注册到多路复用器上，发送请求消息，读应答
			//经测试，一般第一次是连接不上的（可能是异步连接的原因），所以必须注册connect到Selector以反复重连，直到成功！
			if(socketChannel.connect(new InetSocketAddress("127.0.0.1",8888))){//异步连接，直至成功
				socketChannel.register(selector, SelectionKey.OP_READ);
				//本客户向外写东西
				//因为第一次连接不上，所以这里的写入并没有执行，所以只执行了下面的发送1000此操作，而不是1001次！！
				byte[] req = ("QUERY_TIME" + System.getProperty("line.separator")).getBytes();
				ByteBuffer writeBuffer = ByteBuffer.allocate(req.length);
				writeBuffer.put(req);
				writeBuffer.flip();
				socketChannel.write(writeBuffer);
				if(!writeBuffer.hasRemaining()){
					System.out.println("Send order 2 server succeed.");
				}
			}else{//注册连接事件，轮询直至连接成功
				//异步，到底是什么概念？底层是什么原理？TCP/IP层面
				System.out.println("Not Connected Yet!");
				socketChannel.register(selector, SelectionKey.OP_CONNECT);
			}
			while(true) {//直接无限循环，防止客户端断开造成接收服务端写入失败
				selector.select();
				Set<SelectionKey> selectionKeys = selector.selectedKeys();
				Iterator<SelectionKey> it = selectionKeys.iterator();
				SelectionKey selectionKey = null;
				while(it.hasNext()) {
					selectionKey = it.next();
					it.remove();//非常重要！！每次处理过的要及时删除！否则空指针异常！！
					if(selectionKey.isValid()){
						SocketChannel sc = (SocketChannel) selectionKey.channel();
			            if(selectionKey.isConnectable()){
			                if(sc.finishConnect()){
			                	sc.register(selector, SelectionKey.OP_READ);//如果上次连接失败，这里重新连接，成功后重新注册read
			    				//本客户向外写东西
			                	//模拟不断传输数据的粘包拆包问题
			                	for(int i=0;i<1000;i++) {
			                		//模拟按换行符编码
			                		byte[] req = ("QUERY_TIME" + System.getProperty("line.separator")).getBytes();
			                		ByteBuffer writeBuffer = ByteBuffer.allocate(req.length);
			                		writeBuffer.put(req);
			                		writeBuffer.flip();
			                		socketChannel.write(writeBuffer);
			                		if(!writeBuffer.hasRemaining()){
			                			System.out.println("Send order 2 server succeed.");
			                		}
			                	}
			                }
			            }
						if(selectionKey.isReadable()){
							try {
								ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
								int readBytes = socketChannel.read(byteBuffer);
								byte[] bytes;
								String message;
								String msg = "";
								while(readBytes > 0) {
									byteBuffer.flip();//将position置于初始位置，limit置于position位置，用于读取position到limit之间的字节（如果没有这句，position和limit应该都在position位置）
									bytes = new byte[byteBuffer.remaining()];//非常重要！设定为可读取长度，否则下面的byteBuffer.get(bytes);操作会出现ByteBuffer下溢错误！！
									//保证每次byteBuffer的内容完全被读出
									byteBuffer.get(bytes);
									message = msg + new String(bytes,"UTF-8");//使用字符串和上次剩余字节进行拼接，注意网络流的大端模式，上次字节要在前面！！
									message = new String(message.getBytes("UTF-8")).trim();//重新化作byte数组再转回字符串，防止有些大字节字符被分拆成多个的情况
									while(message.indexOf(System.getProperty("line.separator")) > -1) {//粘包拆包：分隔符可能被切开导致检测不到，可能在下一个包里，所以需要规定报文最大长度，记录位置和拼接
										//手动去除掉第一个字符就是分隔符的情况，和开头连续分隔符的情况
										while(message.indexOf(System.getProperty("line.separator")) == 0) {
											if(message.length() == (System.getProperty("line.separator")).length()) {
												message = "";
											}else {
												message = message.substring(message.indexOf(System.getProperty("line.separator")) + (System.getProperty("line.separator")).length());
											}
										}
										if(message.indexOf(System.getProperty("line.separator")) > -1) {
											msg = message.substring(0, message.indexOf(System.getProperty("line.separator")));
											//业务处理
											System.out.println("Now1 is : " + msg);
											cnt++;
											System.out.println("Response Count:" + cnt);
											message = message.substring(message.indexOf(System.getProperty("line.separator")) + (System.getProperty("line.separator")).length());
										}
									}
									//用字符串记录剩余字节（除去非业务数据的分隔符）
									msg = message;
									//经测试，这里也出现了与下一个轮询的粘包问题，这里需要想办法判断是否为完整报文，是则处理业务，并置为空串，防止粘包，现实开发可使用定长方式制定协议，使用定长判断
									if(msg.length() == (System.currentTimeMillis() + "").length()) {
										//业务处理
										System.out.println("Now2 is : " + msg);
										cnt++;
										System.out.println("Response Count:" + cnt);
										msg = "";//重要！！
									}
									
									//重新申请空间，再读
									byteBuffer = ByteBuffer.allocate(1024);
									readBytes = socketChannel.read(byteBuffer);
								}
								//最后剩余的报文处理（如果符合命令报文格式，也需要返回数据，如果不符合，直接丢弃）
								//业务处理
								//这里也可能出现多次，因为在一个selectionKey.isReadable()里，而selector是在一个while里面无限轮询的，需要判断报文是否符合业务报文再进行业务处理（可能是空串）
								if(msg.length() == (System.currentTimeMillis() + "").length()) {
									System.out.println("Now3 is : " + msg);
									cnt++;
									System.out.println("Response Count:" + cnt);
								}
							} catch (Exception e) {
								selectionKey.cancel();//取消客户端，详见该方法注释！
								System.out.println("A Socket Client Closed!");
								e.printStackTrace();
							}
							
//							ByteBuffer readBuffer = ByteBuffer.allocate(1024);
//							int readBytes = sc.read(readBuffer);//读到缓冲
//							if(readBytes > 0){
//								readBuffer.flip();
//								byte[] bytes = new byte[readBuffer.remaining()];//缓冲中有多少个字节数据
//								readBuffer.get(bytes);
//								String body = new String(bytes,"UTF-8");
//								System.out.println("Now is : " + body);
//							}else if(readBytes < 0){
//								//贵在坚持！
//								//对端链路关闭
//								selectionKey.cancel();
//								sc.close();
//							}else{
//								;//读到0字节，忽略
//							}
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}//懂得结合此前努力的成果物，即时它有错误，是半成品，也一定好过从头开始，打击自信，造成一开始就抗拒，拖延，始终不做！！
}
