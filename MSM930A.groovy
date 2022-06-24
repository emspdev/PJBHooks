import com.mincom.ellipse.edoi.ejb.msf010.MSF010Key
import com.mincom.ellipse.edoi.ejb.msf010.MSF010Rec
import com.mincom.ellipse.ejra.mso.GenericMsoRecord
import com.mincom.ellipse.ejra.mso.MsoErrorMessage
import com.mincom.ellipse.ejra.mso.MsoField
import com.mincom.ellipse.hook.hooks.MSOHook
import com.mincom.eql.Query
import com.mincom.eql.impl.QueryImpl
import groovy.sql.Sql
import org.apache.commons.lang.StringEscapeUtils

import javax.naming.InitialContext

public class MSM930A extends MSOHook{
    String getHostUrl(String hostName){
        String result
        String instance

        InitialContext initialContext = new InitialContext()
        Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
        Sql sql = new Sql(dataSource)

        if (hostName.contains("ellprd")){
            instance = "ELLPRD"
        }
        else if (hostName.contains("elltrn")){
            instance = "ELLTRN"
        }
        else if (hostName.contains("elltst")){
            instance = "ELLTST"
        }
        else {
            instance = "ELLDEV"
        }

        String queryMSF010 = "select table_desc as tableDesc from msf010 where table_type = '+MAX' and table_code = '$instance'"
        Object queryMSF010Result = sql.firstRow(queryMSF010)
        result = queryMSF010Result ? queryMSF010Result.tableDesc ? queryMSF010Result.tableDesc.trim(): "" : ""

        return result
    }

    def getConfig(String hostName){
        ArrayList result = []
        String instance

        if (hostName.contains("ellprd")){
            instance = "ELLPRD"
        }
        else if (hostName.contains("elltrn")){
            instance = "ELLTRN"
        }
        else if (hostName.contains("elltst")){
            instance = "ELLTST"
        }
        else {
            instance = "ELLDEV"
        }

        Query queryMSF010 = new QueryImpl(MSF010Rec.class).and(MSF010Key.tableType.equalTo("+MAX")).and(MSF010Key.tableCode.equalTo(instance))
        MSF010Rec msf010Rec = tools.edoi.firstRow(queryMSF010)

        if (msf010Rec){
            result.add(msf010Rec.getTableDesc().trim())
            result.add(msf010Rec.getActiveFlag().trim())
        }

        return result
    }

    def integrationMaximo(GenericMsoRecord screen, String hostUrl){
        String AccountCode = screen.getField("EXP_ELEMENT1I").getValue().trim()
        String AccountDesc = screen.getField("EXP_ELE_DESC1I").getValue().trim()
        String ActiveStatus = screen.getField("ACTIVE_STATUS1I").getValue()
        String FKeys1I = screen.getField("FKEYS1I").getValue().trim()
        String Option1 = screen.getField("OPTION1I").getValue()
        String confDel = screen.getField("CONF_DEL1I").getValue().trim()

        String Action = screen.nextAction
        String activeStat = ""
        MsoField errField = new MsoField()

//      mendefinisikan variable "postUrl" yang akan menampung url tujuan integrasi ke API Maximo
        String postUrl = "${hostUrl}/meaweb/es/EXTSYS1/MXE-GLCOMP-XML"
        AccountDesc = StringEscapeUtils.escapeXml(AccountDesc)

        if (ActiveStatus){
            if (ActiveStatus.trim() != ""){
                if (ActiveStatus.trim() == "A"){
                    activeStat = "1"
                } else {
                    activeStat = "0"
                }
            } else {
                activeStat = "1"
            }
        }

        log.info("activeStat: $activeStat")
        String xmlMessage = ""

        if (Option1 == "1" || Option1 == "2"){
            if (FKeys1I == "XMIT-Confirm"){
                xmlMessage = "<SyncMXE-GLCOMP-XML xmlns=\"http://www.ibm.com/maximo\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" creationDateTime=\"2021-04-15T11:37:06+07:00\" baseLanguage=\"EN\" transLanguage=\"EN\" event=\"0\"  maximoVersion=\"7620190514-1348V7611-365\">\n" +
                        "    <MXE-GLCOMP-XMLSet>\n" +
                        "        <GLCOMPONENTS>\n" +
                        "            <ACTIVE>$activeStat</ACTIVE>\n" +
                        "            <COMPTEXT>$AccountDesc</COMPTEXT>\n" +
                        "            <COMPVALUE>$AccountCode</COMPVALUE>\n" +
                        "            <EXTERNALREFID></EXTERNALREFID>\n" +
                        "            <GLORDER>7</GLORDER>\n" +
                        "            <ORGID>UBPL</ORGID>\n" +
                        "            <OWNERSYSID></OWNERSYSID>\n" +
                        "            <SENDERSYSID></SENDERSYSID>\n" +
                        "            <SOURCESYSID></SOURCESYSID>\n" +
                        "            <USERID></USERID>\n" +
                        "        </GLCOMPONENTS>\n" +
                        "    </MXE-GLCOMP-XMLSet>\n" +
                        "</SyncMXE-GLCOMP-XML>"

                log.info("ARSIADI --- XML: $xmlMessage")
                log.info("PostUrl: $postUrl")
                log.info("Active Status: $activeStat")
                log.info("Active Status: ${screen.getField("ACTIVE_STATUS1I").getValue()}")
                log.info("AccountCode: $AccountCode")
                log.info("Account Description: $AccountDesc")

// proses berikut menjelaskan urutan pengiriman data ke API Maximo
                def url = new URL(postUrl)
                HttpURLConnection authConn = url.openConnection()
                authConn.setRequestMethod("POST")
                authConn.setDoOutput(true)
                authConn.setRequestProperty("Content-Type", "application/xml")
                authConn.setRequestProperty("maxauth", "bXhpbnRhZG06bXhpbnRhZG0=")

// pada baris ini, pesan yang sudah diformat dalam bentuk xml dikirimkan ke API Maximo
                try{
                    authConn.getOutputStream().write(xmlMessage.getBytes("UTF-8"))
                    log.info("responsecode: ${authConn.getResponseCode()}")
                    if (authConn.getResponseCode() != 200){
                        String exceptionMsg = authConn.getInputStream().getText()
                        log.info("exceptionMsg: $exceptionMsg")
                        String responseMessage = exceptionMsg
                        log.info("responseMessage: ${responseMessage.trim()}")
                        String errorCode = "9999"
                        screen.setErrorMessage(
                                new MsoErrorMessage("",
                                        errorCode,
                                        responseMessage,
                                        MsoErrorMessage.ERR_TYPE_ERROR,
                                        MsoErrorMessage.ERR_SEVERITY_UNSPECIFIED))

                        errField.setName("COST_CTRE_SEG1I")
                        screen.setCurrentCursorField(errField)
// jika error maka kembalikan request / input ke layar ellipse
                        return screen
                    }
                } catch(Exception e){
// membaca response dari API Maximo. Jika response code bukan "200" berarti error
                    log.info("Exception: ${e}")
                    if (authConn.getResponseCode() != 200) {
                        String responseMessage = authConn.content.toString()
                        log.info("responseMessage: ${responseMessage.trim()}")
                        String errorCode = "9999"
                        screen.setErrorMessage(
                                new MsoErrorMessage("",
                                        errorCode,
                                        responseMessage,
                                        MsoErrorMessage.ERR_TYPE_ERROR,
                                        MsoErrorMessage.ERR_SEVERITY_UNSPECIFIED))

                        errField.setName("COST_CTRE_SEG1I")
                        screen.setCurrentCursorField(errField)
// jika error maka kembalikan request / input ke layar ellipse
                        return screen
                    }
                }
            }
        }
        else{
            if (confDel == "Y"){
                xmlMessage = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<SyncMXE-GLCOMP-XML xmlns=\"http://www.ibm.com/maximo\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" creationDateTime=\"2021-04-15T11:37:06+07:00\" baseLanguage=\"EN\" transLanguage=\"EN\" event=\"0\"  maximoVersion=\"7620190514-1348V7611-365\">\n" +
                        "    <MXE-GLCOMP-XMLSet>\n" +
                        "        <GLCOMPONENTS action=\"Delete\">\n" +
                        "            <ACTIVE>$activeStat</ACTIVE>\n" +
                        "            <COMPTEXT>$AccountDesc</COMPTEXT>\n" +
                        "            <COMPVALUE>$AccountCode</COMPVALUE>\n" +
                        "            <EXTERNALREFID></EXTERNALREFID>\n" +
                        "            <GLORDER>7</GLORDER>\n" +
                        "            <ORGID>UBPL</ORGID>\n" +
                        "            <OWNERSYSID></OWNERSYSID>\n" +
                        "            <SENDERSYSID></SENDERSYSID>\n" +
                        "            <SOURCESYSID></SOURCESYSID>\n" +
                        "            <USERID></USERID>\n" +
                        "        </GLCOMPONENTS>\n" +
                        "    </MXE-GLCOMP-XMLSet>\n" +
                        "</SyncMXE-GLCOMP-XML>"

                log.info("ARS --- XML: $xmlMessage")
                log.info("PostUrl: $postUrl")
                log.info("Active Status: $activeStat")
                log.info("Active Status: ${screen.getField("ACTIVE_STATUS1I").getValue()}")
                log.info("AccountCode: $AccountCode")
                log.info("Account Description: $AccountDesc")

// proses berikut menjelaskan urutan pengiriman data ke API Maximo
                def url = new URL(postUrl)
                HttpURLConnection authConn = url.openConnection()
                authConn.setRequestMethod("POST")
                authConn.setDoOutput(true)
                authConn.setRequestProperty("Content-Type", "application/xml")
                authConn.setRequestProperty("maxauth", "bXhpbnRhZG06bXhpbnRhZG0=")

// pada baris ini, pesan yang sudah diformat dalam bentuk xml dikirimkan ke API Maximo
                try{
                    authConn.getOutputStream().write(xmlMessage.getBytes("UTF-8"))
                    log.info("responsecode: ${authConn.getResponseCode()}")
                    if (authConn.getResponseCode() != 200){
                        String exceptionMsg = authConn.getInputStream().getText()
                        log.info("exceptionMsg: $exceptionMsg")
                        String responseMessage = exceptionMsg
                        log.info("responseMessage: ${responseMessage.trim()}")
                        String errorCode = "9999"
                        screen.setErrorMessage(
                                new MsoErrorMessage("",
                                        errorCode,
                                        responseMessage,
                                        MsoErrorMessage.ERR_TYPE_ERROR,
                                        MsoErrorMessage.ERR_SEVERITY_UNSPECIFIED))

                        errField.setName("COST_CTRE_SEG1I")
                        screen.setCurrentCursorField(errField)
// jika error maka kembalikan request / input ke layar ellipse
                        return screen
                    }
                } catch(Exception e){
// membaca response dari API Maximo. Jika response code bukan "200" berarti error
                    log.info("Exception: ${e}")
                    if (authConn.getResponseCode() != 200) {
                        String responseMessage = authConn.content.toString()
                        log.info("responseMessage: ${responseMessage.trim()}")
                        String errorCode = "9999"
                        screen.setErrorMessage(
                                new MsoErrorMessage("",
                                        errorCode,
                                        responseMessage,
                                        MsoErrorMessage.ERR_TYPE_ERROR,
                                        MsoErrorMessage.ERR_SEVERITY_UNSPECIFIED))

                        errField.setName("COST_CTRE_SEG1I")
                        screen.setCurrentCursorField(errField)
// jika error maka kembalikan request / input ke layar ellipse
                        return screen
                    }
                }
            }
        }
    }

    @Override
    public GenericMsoRecord onPreSubmit(GenericMsoRecord screen){
        String hostname
        InetAddress ip
        ip = InetAddress.getLocalHost()
        hostname = ip.getHostName()
        ArrayList config = getConfig(hostname)
        String hostUrl = config[0] ? config[0].toString().trim() != "" ? config[0].toString().trim() : "" : ""
        Boolean active = config[1] ? config[1] == "Y" ? true : false : false

        if (hostUrl != "" && active){
            integrationMaximo(screen, hostUrl)
        }
    }
}
