package com.xiaobai.baseT15;

import com.neusoft.avnc.sdk.modle.t15.BaseT15;

/**  
* 创建时间：2018年7月18日 下午5:24:20  
* 项目名称：NioServer  
* @author Daniel Bai 
* @version 1.0   
* @since JDK 1.6.0_21  
* 文件名称：BaseT15Unbuild.java  
* 类说明：  
*/
public class BaseT15Unbuild {
	
	public static void main(String[] args) {
		BaseT15 base = new BaseT15();
		base.setEncryptBody(true);
		base.setEncryptVin(true);
		String key = "335C5811A91FE97A3461B317DC317DD1";//18
		key = "482A62ADEB118FD5F2470E2F1977C689";//86
	}
}
