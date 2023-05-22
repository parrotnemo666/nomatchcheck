package com.check;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class CheckNoMatch {

//	private static Logger logger = Logger.getLogger(CheckNoMatch.class);

	static class LoadProperties {
		private static Properties props;

		public static Properties load(String PropertiesPath) {
			props = new Properties();
			try {
				props.load(new FileInputStream(PropertiesPath));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return props;
		}
//
	}

	public static void main(String[] args) throws ClassNotFoundException, SecurityException, IOException {
		// 本機環境
//		Properties props = LoadProperties.load("src/application.properties");
//		PropertyConfigurator.configure("src/log4j.properties"); 
		// 測試&正式環境
		Properties props = LoadProperties.load("/gcti/checknomatchtest/application.properties");
//		PropertyConfigurator.configure("/gcti/checknomatchtest/log4j.properties");

//		logger.info("執行告警程式");

		List<String> keyword = Arrays.asList(props.getProperty("check.keyword").split(","));
		String gctisql = (props.getProperty("datasource.url"));
		String gctiusername = (props.getProperty("datasource.username"));
		String gctipassword = (props.getProperty("datasource.password"));
//
		String oraclesql = (props.getProperty("datasource.url2"));
		String oracleusername = (props.getProperty("datasource.username2"));
		String oraclepassword = (props.getProperty("datasource.password2"));
		String resultsetsql = (props.getProperty("sql.ResultSet"));

		AtomicInteger i = new AtomicInteger(0);
		int allowable = Integer.parseInt((props.getProperty("check.allow")));

		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		Class.forName("oracle.jdbc.OracleDriver");
		try (

				Connection con = DriverManager.getConnection(gctisql, gctiusername, gctipassword);
				Connection con1 = DriverManager.getConnection(oraclesql, oracleusername, oraclepassword);
				Statement stmt = con.createStatement();
				ResultSet rs = stmt.executeQuery(resultsetsql);
				Statement stmt1 = con1.createStatement();
//				
		) {
			System.out.println("執行AI流程異常告警");
//			logger.info("執行AI流程異常告警");
			List<String> testList = new ArrayList<String>();
			i.set(0);
			while (rs.next()) {
				System.out.println(rs.getString(1));
//				logger.info("CallUUID偵測 " + rs.getString(1));

				keyword.forEach(s -> {
					try {
						if (rs.getString("Reason_Memo").contains(s)) {
							i.addAndGet(1); // 有符合就加1
							testList.add(String.valueOf(rs.getString("CallUUID") + "," + rs.getString("Flow_id") + ","
									+ rs.getString("Reason_Memo")));
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				});
			}

			if (i.get() >= allowable) {
				Date today = new Date();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String currdate = sdf.format(today);
				System.out.println("已達3次，發AI流程異常告警");
//				logger.info("已達3次，發小i異常告警");
				// System.out.println(testList);
				String Email_SUBJECT = "csr_voice AI流程異常告警 " + currdate;
				String Msg_CONTENT = "csr_voice AI流程異常告警 " + currdate;
				String str = "";
				for (String check : testList) {
					str += check + "; ";
				}

				// 模組化-------------------

				String Email_CONTENT = str;
				System.out.println(Email_CONTENT);
//				logger.info(Email_CONTENT);
				String customerID = "";

				// 配置檔讀取簡訊&Email清單
				List<String> phonelist = Arrays.asList(props.getProperty("phone.list").split(","));
				List<String> emaillist = Arrays.asList(props.getProperty("email.list").split(","));

				//發簡訊
				for (String phonenum : phonelist) {

					String msgsql = "INSERT INTO AG_SEND_MESS_DATA ("
							+ "IS_SEND,IS_REVIEW,SEND_TO_REVIEW,MODIFY_ID,CREATE_ID,IDENTITY_FIELD,MESS_BUS_TYPE, MESS_SEND_TYPE, MESS_TYPE_CODE,  MESS_ITEM_CODE, CONTENT, SEND_TO, CREATE_NAME, CUST_ID "
							+ ") VALUES('W','N','N','00000','00000',SQ_SEND_MESS_DATA.NEXTVAL,'2','1','W', 'W12', '"
							+ Msg_CONTENT + "','" + phonenum + "','GVP','')";
					int rs0_1 = stmt1.executeUpdate(msgsql);
				}

				// 發email
				for (String email : emaillist) {

					String emailsql = "INSERT INTO AG_SEND_MESS_DATA ("
							+ "IS_SEND,IS_REVIEW,SEND_TO_REVIEW,MODIFY_ID,CREATE_ID,IDENTITY_FIELD,MESS_BUS_TYPE, MESS_SEND_TYPE, MESS_TYPE_CODE,  MESS_ITEM_CODE, SUBJECT,  CONTENT,   SEND_TO,  CREATE_NAME, CUST_ID "
							+ ")VALUES('W','N','N','00000','00000',SQ_SEND_MESS_DATA.NEXTVAL,'2','2','W', 'W12', '"
							+ Email_SUBJECT + "','" + Email_CONTENT + "','" + email + "','GVP','" + customerID + "' )";
					int rs0_2 = stmt1.executeUpdate(emailsql);
				}
				// 模組化-------------------

		}

		} catch (Exception e) {
			System.out.println(e);
		}

	}

}
