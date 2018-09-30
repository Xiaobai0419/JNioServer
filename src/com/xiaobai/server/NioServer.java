package com.xiaobai.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**  
* 创建时间：2018年7月17日 下午1:24:44  
* 项目名称：NioServer  
* @author Daniel Bai 
* @version 1.0   
* @since JDK 1.6.0_21  
* 文件名称：NioServer.java  
* 类说明：  
*/
public class NioServer implements Runnable{

	private NioServer(){
		
		
	}
	
	public static NioServer getInstance() {
		return NioServerHolder.getInstance();
	}
	
	private static class NioServerHolder {
		
		private static NioServer instance = new NioServer();
		
		public static NioServer getInstance() {
			return instance;
		}
	}
	
	private ServerSocketChannel serverSocketChannel;
	
	private Selector selector;
	
	private Integer port;
	
	public synchronized void build(int port) throws IOException {
		this.port = port;

	}
	
	private int cnt = 0;
	
	@Override
	public void run() {
		if(port == null || port == 0) {
			System.out.println("NioServer Not Build Yet.Please Build First!");
			return;
		}
		try {
			selector = Selector.open();
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);//必须配置false!否则报阻塞状态异常！！
			//注意，是获取socket后bind,不是直接bind！！！
			serverSocketChannel.socket().bind(new InetSocketAddress(this.port), 1024);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);//向Selector注册accept事件，以监听SocketChannel连接
			System.out.println("NioServer Successfully Started At Port:" + this.port);
			//epoll轮询，selector阻塞，I/O非阻塞，只轮询到就绪I/O
			while(true) {
				selector.select();
				Set<SelectionKey> selectionKeys = selector.selectedKeys();
				Iterator<SelectionKey> it = selectionKeys.iterator();
				SelectionKey selectionKey = null;
				while(it.hasNext()) {
					selectionKey = it.next();
					it.remove();//非常重要！！每次处理过的要及时删除！否则空指针异常！！
					if(selectionKey.isValid()){
						if(selectionKey.isAcceptable()) {
							ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
							//必须有这句：手动accept!!（isAcceptable代表内核连接队列中三次握手成功的连接，accept是从该队列取一个这样的连接！）
							SocketChannel socketChannel = serverSocketChannel.accept();
							socketChannel.configureBlocking(false);//必须配置false!否则报阻塞状态异常！！
							socketChannel.register(selector, SelectionKey.OP_READ);//每个SocketChannel连接成功后，向Selector注册read事件，以监听其可读事件
						}
						if(selectionKey.isReadable()) {
							/**
							 *这里的客户端SocketChannel源于上面if中注册了read事件的SocketChannel，所以即使客户端断连，这里还是有其注册记录，客户端断连，服务端代码在其后的循环中走到这里时，就会发生
							      连接关闭异常，catch中进行了捕捉，并取消了该键，也就是这时才取消该SocketChannel的注册记录，那么下次循环时Selector才不会轮询到该键（这时该键为非valid了），就不会再出现失效
							      的注册记录连接关闭异常的情况了！
							 *客户端关闭的异常如下：
								A Socket Client Closed!
								java.io.IOException: 远程主机强迫关闭了一个现有的连接。
									at sun.nio.ch.SocketDispatcher.read0(Native Method)
									at sun.nio.ch.SocketDispatcher.read(SocketDispatcher.java:43)
									at sun.nio.ch.IOUtil.readIntoNativeBuffer(IOUtil.java:223)
									at sun.nio.ch.IOUtil.read(IOUtil.java:197)
									at sun.nio.ch.SocketChannelImpl.read(SocketChannelImpl.java:379)
									at com.xiaobai.server.NioServer.run(NioServer.java:94)
									at java.lang.Thread.run(Thread.java:745)
							 */
							SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
							//每个进行捕捉，防止某个客户端异常断连造成整个服务端异常，服务端整体退出！！
							try {
//								socketChannel.register(selector, SelectionKey.OP_WRITE);//每个SocketChannel可读后，向Selector注册write事件，以监听其可写事件
								ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
								int readBytes = socketChannel.read(byteBuffer);
								byte[] bytes;
								String message;
								String msg = "";
								/**
								 *ByteBuffer固定申请了1024内存，每次读取不会超过这个值，规定分隔符分割的报文长度不超过2048（私有通信协议），那么存储每次分隔符造成的冗余byte数组设定为2048，
								      也就是和下次循环传输的字节拼接成完整的分隔符分割的报文长度不会超过2048--这种思路可用于定长协议解码器 
								 */
//								byte[] totalBytes = new byte[2048];
								while(readBytes > 0) {
									byteBuffer.flip();//将position置于初始位置，limit置于position位置，用于读取position到limit之间的字节（如果没有这句，position和limit应该都在position位置）
									bytes = new byte[byteBuffer.remaining()];//非常重要！设定为可读取长度，否则下面的byteBuffer.get(bytes);操作会出现ByteBuffer下溢错误！！
									//保证每次byteBuffer的内容完全被读出
									byteBuffer.get(bytes);
									message = msg + new String(bytes,"UTF-8");//使用字符串和上次剩余字节进行拼接，注意网络流的大端模式，上次字节要在前面！！
									message = new String(message.getBytes("UTF-8")).trim();//重新化作byte数组再转回字符串，防止有些大字节字符被分拆成多个的情况
									//使用while兼顾小报文（一次轮询出多条，多个分隔符）
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
											System.out.println("Received Message1:" + msg);
											if("QUERY_TIME".equalsIgnoreCase(msg)) {
												this.cnt++;
												System.out.println("Query Count:" + this.cnt);
												String tmpResponse = System.currentTimeMillis() + "" + System.getProperty("line.separator");
												bytes = tmpResponse.getBytes("UTF-8");
												byteBuffer = ByteBuffer.allocate(bytes.length);
												byteBuffer.put(bytes);
												byteBuffer.flip();
												socketChannel.write(byteBuffer);
												System.out.println("Respose Write Back:" + tmpResponse);
											}
											message = message.substring(message.indexOf(System.getProperty("line.separator")) + (System.getProperty("line.separator")).length());//注意分隔符长度不一定是1！！
										}
									}
									//用字符串记录剩余字节（除去非业务数据的分隔符）
									msg = message;
									//经测试，这里的msg也可能是完整指令，如不处理，又没有分隔符，被带入下次readBytes，与其message连接，造成指令连接（手动造成了粘包），吞噬了报文，也没有回复消息给客户端
									if("QUERY_TIME".equalsIgnoreCase(msg)) {
										this.cnt++;
										System.out.println("Received Message2:" + msg);
										System.out.println("Query Count:" + this.cnt);
										String tmpResponse = System.currentTimeMillis() + "" + System.getProperty("line.separator");
										bytes = tmpResponse.getBytes("UTF-8");
										byteBuffer = ByteBuffer.allocate(bytes.length);
										byteBuffer.put(bytes);
										byteBuffer.flip();
										socketChannel.write(byteBuffer);
										System.out.println("Respose Write Back:" + tmpResponse);
										msg = "";
									}
									
									//重新申请空间，再读
									byteBuffer = ByteBuffer.allocate(1024);
									readBytes = socketChannel.read(byteBuffer);
								}
								//最后剩余的报文处理（如果符合命令报文格式，也需要返回数据，如果不符合，直接丢弃）
								//业务处理,msg一定是没有分隔符的
								//这里也可能出现多次，因为在一个selectionKey.isReadable()里，而selector是在一个while里面无限轮询的，需要判断报文是否符合业务报文再进行业务处理（可能是空串）
								if("QUERY_TIME".equalsIgnoreCase(msg)) {
									System.out.println("Received Message3:" + msg);
									this.cnt++;
									System.out.println("Query Count:" + this.cnt);
									String tmpResponse = System.currentTimeMillis() + "" + System.getProperty("line.separator");
									bytes = tmpResponse.getBytes("UTF-8");
									byteBuffer = ByteBuffer.allocate(bytes.length);
									byteBuffer.put(bytes);
									byteBuffer.flip();
									socketChannel.write(byteBuffer);
									System.out.println("Respose Write Back:" + tmpResponse);
								}
								/**
								这里仍然有一个问题：selectionKey.isReadable()也不能保证发送到服务端缓冲里的数据是完整的业务报文（想办法客户端刷新操作，是否能保证？），需要更高层次的截断数据保留操作：
								但不能简单将这里的msg提升为全局变量，因为每次轮询read事件出来，剩余的msg和下次的轮询报文很可能不是同一个客户端的数据，需要在处理多用户并发处理中想办法为每个客户端的上一次保留数据和每次轮询报文加上客户端标识进行判断，避免不同客户端的数据混淆错乱
								这里简单粗暴的操作就是丢包：丢掉这里的每次剩余的不符合业务报文的msg和同一个客户端下一次开头本应接续本次剩余成为完整报文的部分，后果就是造成客户端不是每次下达有效报文命令都能成功获取服务端应答，因为有这种服务端主动丢包的操作
								 */
								
//								int readBytes = socketChannel.read(byteBuffer);//客户端最好使用while(true)始终保持连接，否则这里可能在客户端断连时出现异常（比如socketChannel空指针等）
//								if(readBytes > 0) {//这里必须判断，否则byteBuffer.get(bytes);会出现错误！
//									byteBuffer.flip();//将position置于初始位置，limit置于position位置，用于读取position到limit之间的字节（如果没有这句，position和limit应该都在position位置）
//									byte[] bytes = new byte[byteBuffer.remaining()];//非常重要！设定为可读取长度，否则下面的byteBuffer.get(bytes);操作会出现ByteBuffer下溢错误！！
//									byteBuffer.get(bytes);
////									int pos = 0;
////									int destPos = 0;
////									while(byteBuffer.hasRemaining()) {
////										System.arraycopy(bytes, 0, totalBytes, destPos, byteBuffer.position() - pos);
////										destPos+=(byteBuffer.position() - pos);
////										pos = byteBuffer.position();
////									}
//									String message = new String(bytes,"UTF-8");
//									System.out.println("Received Message:" + message.trim());
//									if("QUERY_TIME".equalsIgnoreCase(message.trim())) {
//										bytes = (System.currentTimeMillis() + "").getBytes("UTF-8");
//										byteBuffer = ByteBuffer.allocate(bytes.length);
//										byteBuffer.put(bytes);
//										byteBuffer.flip();
//										socketChannel.write(byteBuffer);
//									}
//								}
							} catch (Exception e) {
								//经测试，这里应该去掉异常客户端，否则会发现反复轮询到这个失效客户端的key,造成反复异常！！
								selectionKey.cancel();//取消客户端，详见该方法注释！
								System.out.println("A Socket Client Closed!");
								e.printStackTrace();
							}

						}
//						if(selectionKey.isWritable()) {
//							//TODO
//							SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
//							ByteBuffer byteBuffer = ByteBuffer.allocate(10240);
//							byte[] bytes = new byte[2048];
//							bytes = (System.currentTimeMillis() + "").getBytes("UTF-8");
//							byteBuffer.wrap(bytes);
//							socketChannel.write(byteBuffer);
//						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//TODO 1.粘包拆包 2.缓冲区 3.ByteBuffer原理与使用（尤其flip,put,position,limit） 4.注册写事件 5.私有协议数据传输与解析 6.编解码 7.反复传输数据，每次传输数据隔断，解析（还是粘包拆包） 8.高并发下的处理（每个客户端检测，业务线程池，断线重连问题，及时去除无效客户端问题） 9.多线程与内存溢出 10.处理速度和吞吐量优化（阻塞队列，阻塞操作与异步操作等，结合mq的发送、接收处理）
	
	public static void main(String[] args) {
		byte[] bytes = new byte[1024];
		String s = "sxl0";
		try {
			bytes[0] = (s.getBytes("UTF-8"))[0];
			System.out.println(bytes[0]);//输出其整型值
			bytes[1] = (s.getBytes("UTF-8"))[s.getBytes("UTF-8").length - 1];
			System.out.println(bytes[1]);//输出其整型值
			bytes[2] = (s.getBytes("UTF-8"))[s.getBytes("UTF-8").length - 3];
			System.out.println(bytes[2]);//输出其整型值
			//以上模拟用大byte数组接收网络传输字节，可能有空余元素的场景
			
			String str = new String(bytes,"UTF-8");
			System.out.println("|" + str + "|");
			//以上模拟转码成字符串，有空余元素的转码结果场景
			
			
			System.out.println(str.length());//输出字符串长度，看未赋值元素每个大小
			//以上模拟有空余元素的转码，空余元素转码后所占字符数的场景
			
			System.out.println(bytes[80]);//输出其未赋值元素整型值
			
			str = str.trim();
			System.out.println("|" + str + "|");
			System.out.println(str.length());//字符串去掉空格的长度，即对应byte数组去掉未赋值元素的长度？
			bytes = str.getBytes("UTF-8");//去掉未赋值元素的有效byte?
			for(int i=0;i<bytes.length;i++) {
				System.out.println(bytes[i]);
			}
			//以上模拟使用trim处理字符串，再还原成byte数组，是否能完整还原网络传输的有效字符的场景
			
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
