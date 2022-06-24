import com.mincom.ellipse.edoi.ejb.msf010.MSF010Key
import com.mincom.ellipse.edoi.ejb.msf010.MSF010Rec
import com.mincom.ellipse.hook.hooks.ServiceHook
import com.mincom.ellipse.types.m3140.instances.PurchaseOrderReceiptDTO
import com.mincom.ellipse.types.m3140.instances.PurchaseOrderReceiptServiceResult
import com.mincom.eql.Query
import com.mincom.eql.impl.QueryImpl
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import javax.naming.InitialContext

class PurchaseOrderReceiptService_cancel extends ServiceHook{
    String version = "1"

    InitialContext initialContext = new InitialContext()
    Object CAISource = initialContext.lookup("java:jboss/datasources/ApplicationDatasource")
    def sql = new Sql(CAISource)

    @Override
    Object onPreExecute(Object input){
        PurchaseOrderReceiptDTO purchaseOrderReceiptDTO = (PurchaseOrderReceiptDTO) input

        String changeNumber = purchaseOrderReceiptDTO.changeNumber ? purchaseOrderReceiptDTO.changeNumber.getValue() : ""

        PurchaseOrderReceiptServiceResult purchaseOrderReceiptServiceResult = new PurchaseOrderReceiptServiceResult()

        if (!changeNumber || changeNumber == ""){
            String orderNumber = purchaseOrderReceiptDTO.purchaseOrderNumber ? purchaseOrderReceiptDTO.purchaseOrderNumber.getValue() : ""
            String orderItemNumber = purchaseOrderReceiptDTO.purchaseOrderItemNumber ?purchaseOrderReceiptDTO.purchaseOrderItemNumber.getValue() : ""
            String receiptRef = purchaseOrderReceiptDTO.receiptReference ? purchaseOrderReceiptDTO.receiptReference.getValue() : ""

            String queryMSF222 = "select min(change_no) changeNo from msf222 " +
                    "where po_no = '$orderNumber' " +
                    "and po_item = '$orderItemNumber' " +
                    "and receipt_ref = '$receiptRef' " +
                    "and value_rcvd_loc > 0"

            log.info("queryMSF222: $queryMSF222")

            def queryMSF222Result = sql.firstRow(queryMSF222)
            log.info("queryMSF222Result: $queryMSF222Result")
            if (queryMSF222Result){
                String changeNo = queryMSF222Result.changeNo ? queryMSF222Result.changeNo : ""
                purchaseOrderReceiptDTO.changeNumber.setValue(changeNo)

                purchaseOrderReceiptServiceResult.setPurchaseOrderReceiptDTO(purchaseOrderReceiptDTO)
            }
            log.info("---changeNumber2: ${purchaseOrderReceiptServiceResult.getPurchaseOrderReceiptDTO().changeNumber.getValue()}")
        }
        return null
    }


}
