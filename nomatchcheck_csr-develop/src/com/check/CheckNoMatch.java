package com.check;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class CheckNoMatch {

	private static final Logger logger = LogManager.getLogger(CheckNoMatch.class);

	private static class LoadProperties {
		private static Properties props;

		public static Properties load(String PropertiesPath) {
			props = new Properties();
			try (FileInputStream fis = new FileInputStream(PropertiesPath)) {
				props.load(fis);
			} catch (FileNotFoundException e) {
				logger.error("找不到檔案: ", e);
			} catch (IOException e) {
				logger.error("讀取檔案錯誤: ", e);
			} finally {
				logger.info("Properties loading finished.");				
			}
			return props;
		}
//
	}

	private static String decode(String key) {
		String secretKey = "Hualiteq123$";
		String StringKey = key;
		try {
			key = HQ_AESEncryption.decrypt(key, secretKey);
		   
			return key; 
		} catch (Exception e) {
			logger.debug("解碼失敗: ", e);
		}
		return StringKey; 
	}
	
	public static void main(String[] args) throws SecurityException{
      
		// 測試&正式環境
		Properties props = LoadProperties.load("/gcti/checknomatchtest/application.properties");
		Configurator.initialize(null, "/gcti/checknomatchtest/log4j2.xml");
		logger.info("執行: CSR - AI告警程式 {}", "TEST");
		
		//#region 建置變數
		List<String> keyword = Arrays.asList(props.getProperty("check.keyword").split(","));
		String gctisqlstr = decode((props.getProperty("link.config1")));
		String gctiUsernameStr = decode((props.getProperty("link.config2")));
		String gctiPasswordStr = decode((props.getProperty("link.config3")));

		String gctiSql  = gctisqlstr;
		String gctiUsername = gctiUsernameStr;
		String gctiPassword = gctiPasswordStr;

		String oracleSqlStr = decode((props.getProperty("link.config21")));
		String oracleUsernameStr = decode((props.getProperty("link.config22")));
		String oraclePasswordStr = decode((props.getProperty("link.config23")));
		String resultSetSql = (props.getProperty("sql.ResultSet"));
		
		String oracleSql  = oracleSqlStr;
		String oracleUsername = oracleUsernameStr;
		String oraclePassword = oraclePasswordStr;
		
		int alertCount = 0;
		int allowable = Integer.parseInt((props.getProperty("check.allow")));

		logger.info("執行: Config Load finished.");
		boolean isSend = false;
		//#endregion

		try 
		(			
			//Step 0:建立連線 , 取得當前資料
			Connection connGcti = DriverManager.getConnection(gctiSql, gctiUsername, gctiPassword);
			Connection connOra = DriverManager.getConnection(oracleSql, oracleUsername, oraclePassword);
			Statement stmtGcti = connGcti.createStatement();
			ResultSet rs = stmtGcti.executeQuery(resultSetSql);
			Statement stmtOra = connOra.createStatement();	
		)
		{
			//#region 主流程
			logger.info("執行: CSR - AI流程異常告警");
			
			List<String> testList = new ArrayList<>(); //異常項目紀錄清單
			
			alertCount = checkKeyword(rs, keyword, testList);						
			
			List<String> phoneList = Arrays.asList(props.getProperty("phone.list").split(","));
			List<String> emailList = Arrays.asList(props.getProperty("email.list").split(","));
			logger.info("執行: Alert: {} 筆.", alertCount);
			//Step 2: 發送告警
			if (alertCount >= allowable) {
				// 配置檔讀取簡訊&Email清單
				sendAlertNotify(testList, phoneList, emailList, stmtOra, allowable);
				isSend = true;
		}
		//#endregion
			
			if(!isSend){
				logger.info("未達告警條件，執行遵法告警檢查");
				int time = Integer.parseInt(props.getProperty("rec.check.time"));		
				String unit = props.getProperty("rec.check.unit");
				String recWhereStr = getRecWhereStr(time, unit);
				String recSql = getRecSqlQuery(props, recWhereStr);	
				
				try (// 創建 PreparedStatement
				 PreparedStatement preStat = connGcti.prepareStatement(recSql)) {
					preStat.setInt(1, allowable);
					
					logger.debug(recSql);
					// 執行查詢
					ResultSet rsRec = preStat.executeQuery();								
					//reset variable
					testList.clear();
					alertCount = 0;					
					alertCount = checkKeyword(rsRec, keyword, testList);
				}
				logger.info("執行: Alert: {} 筆.",alertCount);			
				if (alertCount >= allowable) {
					// 配置檔讀取簡訊&Email清單
					sendAlertNotify(testList, phoneList, emailList, stmtOra, allowable);
				}
			}
		} catch (Exception e) {
			logger.error("程式錯誤: ", e);
		}
		logger.info("CSR - AI告警流程結束");
	}

	private static String getRecSqlQuery(Properties props, String recWhereStr) {
		return props.getProperty("sql.Select")
                + props.getProperty("sql.SelectValue")
                + props.getProperty("sql.From")
                + props.getProperty("sql.JoinREC")
				+ recWhereStr
				+ props.getProperty("sql.WhereAndREC")
				+ props.getProperty("sql.Order");
	}

	private static void sendAlertNotify(List<String> testList, List<String> phoneList, List<String> emailList,
			Statement stmtOra, int allowable) throws SQLException {
		Date today = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String currdate = sdf.format(today);
		logger.info("已達 {} 次，發AI流程異常告警.", allowable);
		//確認已達成告警條件 , 不做後續流程
		String emailSubject = "csr_voice AI流程異常告警 " + currdate;
		String msgContent = "csr_voice AI流程異常告警 " + currdate;
		String str = "";
		for (String check : testList) {
			str += check + "; ";
		}

		String emailContent = str;
		logger.info(emailContent);
		String customerID = "";
		
		int sendCount = 0;
		//發簡訊
		for (String phoneNum : phoneList) {

			String msgsql = getSendPhoneSqlQuery(phoneNum, msgContent);
			int rsetOra1 = stmtOra.executeUpdate(msgsql);
			if(rsetOra1 != 1) {
				String warnStr = replaceLastChar(phoneNum,"XXX");
				logger.warn("msg execResult: {}, data:{}.", rsetOra1, warnStr);
			}
			sendCount += rsetOra1;
		}
		logger.info("msg TotalResult:{}", sendCount);
		sendCount = 0;
		// 發email
		for (String email : emailList) {
			String emailsql = getSendEmailSqlQuery(email, emailSubject, emailContent, customerID);	
			int rsetOra2 = stmtOra.executeUpdate(emailsql);
			if(rsetOra2 != 1) {
				String warnStr = replaceLastChar(email,"XXX");
				logger.warn("email execResult: {}, data:{}.", rsetOra2, warnStr);
			}
			sendCount += rsetOra2;
		}
		logger.info("email TotalResult: {}", sendCount);
		
	}

	private static int checkKeyword(ResultSet rs, List<String> keyword, List<String> testList) {
		AtomicInteger i = new AtomicInteger(0);
		i.set(0);
		try {
			while (rs.next()) {
				logger.debug("CallUUID紀錄: {}", rs.getString(1));//設定為Debug等級才紀錄				
			//Step 1: 檢查關鍵字
				keyword.forEach(s -> {
					try {
						if (rs.getString("Reason_Memo").contains(s)) {
							logger.info("偵測紀錄 - keyword:{} ,UUID:{}", s, rs.getString("CallUUID"));
							i.addAndGet(1); // 有符合就加1
							testList.add(String.valueOf(rs.getString("CallUUID") + "," + rs.getString("Flow_id") + ","
									+ rs.getString("Reason_Memo")));
						}
					} catch (SQLException e) {
						logger.error("SQL Result Error", e);
					}
				});
			}
			return i.get();
		} catch (SQLException e) {
			logger.error("SQL Error", e);
		}
		return -1;
	}

	private static String getRecWhereStr(int time, String unit) {
		String unitStr;
		int unitStrLength;

		switch(unit) {
		case "day":
			unitStr = "DD";
			unitStrLength = 10;
			break;
		case "hour":
			unitStr = "HH";
			unitStrLength = 19;
			break;
		case "minute":
		default:
			unitStr = "MINUTE";
			unitStrLength = 19;
		}

		return "where icr.[Begin_Time] between (SELECT DATEADD("
				+ unitStr + ",-" 
				+ time + ",CONVERT(DATETIME,CONVERT(VARCHAR(" 
				+ unitStrLength + "),GETDATE(),120)))) and GETDATE()";
	}
    private static String replaceLastChar(String str, String replacement) {
        if (str == null || str.length() < 3) {
            throw new IllegalArgumentException("字串長度不足三位或為空");
        }
        return str.substring(0, str.length() - 3) + replacement;
    }
    
	private static String getSendPhoneSqlQuery(String phone, String content){
		return "INSERT INTO AG_SEND_MESS_DATA ("
				+ "IS_SEND,IS_REVIEW,SEND_TO_REVIEW,MODIFY_ID,CREATE_ID,IDENTITY_FIELD,MESS_BUS_TYPE, MESS_SEND_TYPE, MESS_TYPE_CODE,  MESS_ITEM_CODE, CONTENT, SEND_TO, CREATE_NAME, CUST_ID "
				+ ") VALUES('W','N','N','00000','00000',SQ_SEND_MESS_DATA.NEXTVAL,'2','1','W', 'W12', '"
				+ content + "','" + phone + "','GVP','')";
		
	}
	private static String getSendEmailSqlQuery(String email, String subject, String content, String customerID){
		return "INSERT INTO AG_SEND_MESS_DATA ("
				+ "IS_SEND,IS_REVIEW,SEND_TO_REVIEW,MODIFY_ID,CREATE_ID,IDENTITY_FIELD,MESS_BUS_TYPE, MESS_SEND_TYPE, MESS_TYPE_CODE,  MESS_ITEM_CODE, SUBJECT,  CONTENT,   SEND_TO,  CREATE_NAME, CUST_ID "
				+ ")VALUES('W','N','N','00000','00000',SQ_SEND_MESS_DATA.NEXTVAL,'2','2','W', 'W12', '"
				+ subject + "','" + content + "','" + email + "','GVP','" + customerID + "' )";
		
	}
}
