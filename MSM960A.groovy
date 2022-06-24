//Revision History
//20210703 - Arsiadi Initial Coding
//.................. Development Integrasi Ellipse - Maximo untuk mentrigger pembuatan atau perubahan COA ke Maximo
//.................. pada saat Create / Modify COA di Ellipse

/*Library yang digunakan*/

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
import javax.sql.DataSource
import java.text.SimpleDateFormat

//definisi class hooks untuk program MSO960 (Screen MSM960A). "Extends MSOHook" perlu ditambahkan di setiap hooks yang dibangun untuk program MSO
class MSM960A extends MSOHook{
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
        //      def keyword digunakan untuk mendefinisikan tipe variable atau function secara umum sehingga definisi variable dengan tipe apa pun bisa menggunakan def
//      new Date() digunakan untuk menangkap (instantiate) tanggal saat ini dari system
        def date = new Date()

//      SimpleDateFormat digunakan untuk menentukan format tanggal yang akan dibaca
        def sdf = new SimpleDateFormat("yyyy-MM-dd")

//      assign variable activeStatDate dengan tanggal hari ini dari system dengan format yang sudah didefinisikan oleh variable "sdf"
        String activeStatDate = sdf.format(date)

//      Instantiate MsoField dan assign ke variable errField untuk menangkap pesan error setelah hooks dijalankan
        MsoField errField = new MsoField()

//      mendefinisikan variable "postUrl" yang akan menampung url tujuan integrasi ke API Maximo
        String postUrl = "${hostUrl}/meaweb/es/EXTSYS1/MXE-COA-XML"

        String AccountCode = ""
        String accountSegment1 = ""
        String accountSegment2 = ""
        String accountSegment3 = ""
        String accountSegment4 = ""
        String accountSegment5 = ""
        String accountSegment6 = ""
        String accountSegment7 = ""
        String expElement = ""
        String accountCodeFormatted = ""
//      StringEscapeUtils.escapeXml berfungsi untuk memastikan tidak ada special character yang mengganggu file XML sehingga tidak bisa terbaca
        String accountDesc = StringEscapeUtils.escapeXml(screen.getField("ACCOUNT_DESC1I1").getValue().trim())
        String ActiveStatus = screen.getField("ACTIVE_STATUS1I1").getValue().trim()
        String action1i1 = screen.getField("ACTION1I1").getValue().trim()
        String activeStat = ""

        if ((screen.getField("ACTIVE_STATUS1I1").getValue().trim() == "A" && screen.nextAction == 0 && screen.getField("FKEYS1I").getValue().trim() == "XMIT-Confirm, F8-Modify") ||
                ((screen.getField("ACTIVE_STATUS1I1").getValue().trim() == "I" || screen.getField("ACTIVE_STATUS1I1").getValue().trim() == "A") && screen.nextAction == 1 && screen.getField("FKEYS1I").getValue().trim() == "XMIT-Update, F7-Next Screen, F8-Create") ||
                (screen.getField("ACTION1I1").getValue() == "D")){
//      definisi variable yang akan dikirimkan ke Maximo
            AccountCode = screen.getField("ACCOUNT1I1").getValue().trim()
            accountSegment1 = AccountCode.substring(0,1).trim()
            accountSegment2 = AccountCode.substring(1,3).trim()
            accountSegment3 = AccountCode.substring(3,5).trim()
            accountSegment4 = AccountCode.substring(5,8).trim()
            accountSegment5 = AccountCode.substring(8,11).trim()
            accountSegment6 = AccountCode.substring(11,13).trim()
            accountSegment7 = AccountCode.substring(13,15).trim()
            expElement = AccountCode.substring(15).trim()
            if (accountSegment2 == "PT") {
                accountSegment2 = "PN"
            }
            accountCodeFormatted = "$accountSegment1-$accountSegment2-$accountSegment3-$accountSegment4-$accountSegment5-$accountSegment6-$accountSegment7-$expElement"

            if (ActiveStatus == "A"){
                activeStat = "1"
            }
            else if (ActiveStatus == "I"){
                activeStat = "0"
            }

            if (accountSegment3 != "33") return null

            String xmlMessage = ""
//      memasang kondisi yang hanya mentrigger integrasi jika status Account Code Aktif (A) dan Function Keys di layar mengandung kata-kata "XMIT-Confirm, F8-Modify"
            if ((screen.getField("ACTIVE_STATUS1I1").getValue().trim() == "A" && screen.nextAction == 0 && screen.getField("FKEYS1I").getValue().trim() == "XMIT-Confirm, F8-Modify") ||
                    ((screen.getField("ACTIVE_STATUS1I1").getValue().trim() == "I" || screen.getField("ACTIVE_STATUS1I1").getValue().trim() == "A") && screen.nextAction == 1 && screen.getField("FKEYS1I").getValue().trim() == "XMIT-Update, F7-Next Screen, F8-Create")){
//      mendefinisikan pesan dalam format XML (sebagai String) untuk dikirimkan ke API Maximo
                xmlMessage = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<SyncMXE-COA-XML xmlns=\"http://www.ibm.com/maximo\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" creationDateTime=\"2021-04-15T11:21:02+07:00\" baseLanguage=\"EN\" transLanguage=\"EN\" event=\"0\" maximoVersion=\"7620190514-1348V7611-365\">\n" +
                        "    <MXE-COA-XMLSet>\n" +
                        "        <CHARTOFACCOUNTS>\n" +
                        "            <GLACCOUNT>$accountCodeFormatted</GLACCOUNT>\n" +
                        "            <GLCOMP01>$accountSegment1</GLCOMP01>\n" +
                        "            <GLCOMP02>$accountSegment2</GLCOMP02>\n" +
                        "            <GLCOMP03>$accountSegment3</GLCOMP03>\n" +
                        "            <GLCOMP04>$accountSegment4</GLCOMP04>\n" +
                        "            <GLCOMP05>$accountSegment5</GLCOMP05>\n" +
                        "            <GLCOMP06>$accountSegment6</GLCOMP06>\n" +
                        "            <GLCOMP07>$accountSegment7</GLCOMP07>\n" +
                        "            <GLCOMP08>$expElement</GLCOMP08>\n" +
                        "            <ORGID>UBPL</ORGID>\n" +
                        "            <ACTIVEDATE>$activeStatDate</ACTIVEDATE>\n" +
                        "            <ACTIVE>$activeStat</ACTIVE>\n" +
                        "        </CHARTOFACCOUNTS>\n" +
                        "    </MXE-COA-XMLSet>\n" +
                        "</SyncMXE-COA-XML>"
            }

            if (screen.nextAction == 1 && action1i1 == "D" && screen.getField("FKEYS1I").getValue().trim() == "XMIT-Update, F7-Next Screen, F8-Create") {
                xmlMessage = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<SyncMXE-COA-XML xmlns=\"http://www.ibm.com/maximo\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" creationDateTime=\"2021-04-15T11:21:02+07:00\" baseLanguage=\"EN\" transLanguage=\"EN\" event=\"0\" maximoVersion=\"7620190514-1348V7611-365\">\n" +
                        "    <MXE-COA-XMLSet>\n" +
                        "        <CHARTOFACCOUNTS action=\"Delete\">\n" +
                        "            <GLACCOUNT>$accountCodeFormatted</GLACCOUNT>\n" +
                        "            <GLCOMP01>$accountSegment1</GLCOMP01>\n" +
                        "            <GLCOMP02>$accountSegment1</GLCOMP02>\n" +
                        "            <GLCOMP03>$accountSegment1</GLCOMP03>\n" +
                        "            <GLCOMP04>$accountSegment1</GLCOMP04>\n" +
                        "            <GLCOMP05>$accountSegment1</GLCOMP05>\n" +
                        "            <GLCOMP06>$accountSegment1</GLCOMP06>\n" +
                        "            <GLCOMP07>$accountSegment1</GLCOMP07>\n" +
                        "            <GLCOMP08>$accountSegment1</GLCOMP08>\n" +
                        "            <ORGID>UBPL</ORGID>\n" +
                        "            <ACTIVEDATE>$activeStatDate</ACTIVEDATE>\n" +
                        "            <ACTIVE>$activeStat</ACTIVE>\n" +
                        "        </CHARTOFACCOUNTS>\n" +
                        "    </MXE-COA-XMLSet>\n" +
                        "</SyncMXE-COA-XML>"
            }

// proses berikut menjelaskan urutan pengiriman data ke API Maximo
            def url = new URL(postUrl)
            HttpURLConnection authConn = url.openConnection()
            authConn.setDoOutput(true)
            authConn.setRequestProperty("Content-Type", "application/xml")
            authConn.setRequestProperty("maxauth", "bXhpbnRhZG06bXhpbnRhZG0=")
            authConn.setRequestMethod("POST")

//          pada baris ini, pesan yang sudah diformat dalam bentuk xml dikirimkan ke API Maximo
            authConn.getOutputStream().write(xmlMessage.getBytes("UTF-8"))
            log.info("responsecode: ${authConn.getResponseCode()}")

//          membaca response dari API Maximo. Jika response code bukan "200" berarti error
            if (authConn.getResponseCode() != 200) {
                String responseMessage = authConn.content.toString()
                log.info("responseMessage: $responseMessage")
                String errorCode = "9999"
                screen.setErrorMessage(
                        new MsoErrorMessage("",
                                errorCode,
                                responseMessage,
                                MsoErrorMessage.ERR_TYPE_ERROR,
                                MsoErrorMessage.ERR_SEVERITY_UNSPECIFIED))

                errField.setName("ACCOUNT1I1")
                screen.setCurrentCursorField(errField)
// jika error maka kembalikan request / input ke layar ellipse
                return screen
            }

// jika tidak error maka kembalikan null
            return null
        }
    }

//  annotation @Override menunjukkan bahwa event onPreSubmit ini akan mengganti class standard Ellipse (jika ada)
//  Untuk program MSO, terdapat 3 event standard yang bisa dimodify dengan tipe parameter GenericMsoRecord:
//  1. onPreSubmit(GeneridMsoRecord request) - dijalankan sebelum fungsi "Submit" standard Ellipse
//  2. onPostSubmit(GenericMsoRecord request, GenericMsoRecord response) - dijalankan setelah fungsi "Submit" standard Ellipse. Terdapat dua parameter yaitu request dan response
//  3. onDisplay(GenericMsoRecord request) - dijalankan pada saat screen dimunculkan

    @Override
    GenericMsoRecord onPreSubmit(GenericMsoRecord screen){
//      membaca informasi instance Ellipse yang sedang aktif dan assign ke variable "ip" dengan tipe InetAddress
        InetAddress ip = InetAddress.getLocalHost()

//      membaca url Ellipse yang sedang aktif dan assign ke variable "hostname" dengan tipe String
        String hostname = ip.getHostName()
        ArrayList config = getConfig(hostname)
        String hostUrl = config[0] ? config[0].toString().trim() != "" ? config[0].toString().trim() : "" : ""
        Boolean active = config[1] ? config[1] == "Y" ? true : false : false

        if (hostUrl != "" && active){
            integrationMaximo(screen, hostUrl)
        }
    }
}
