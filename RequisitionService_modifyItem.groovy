import com.mincom.ellipse.edoi.ejb.msf010.MSF010Key
import com.mincom.ellipse.edoi.ejb.msf010.MSF010Rec
import com.mincom.ellipse.edoi.ejb.msf620.MSF620Key
import com.mincom.ellipse.edoi.ejb.msf620.MSF620Rec
import com.mincom.ellipse.edoi.ejb.msf660.MSF660Key
import com.mincom.ellipse.edoi.ejb.msf660.MSF660Rec
import com.mincom.ellipse.hook.hooks.ServiceHook
import com.mincom.enterpriseservice.ellipse.ErrorMessageDTO
import com.mincom.enterpriseservice.ellipse.dependant.dto.RequisitionItemDTO
import com.mincom.enterpriseservice.ellipse.requisition.RequisitionServiceModifyItemReplyDTO
import com.mincom.enterpriseservice.ellipse.requisition.RequisitionServiceModifyItemRequestDTO
import com.mincom.enterpriseservice.ellipse.workorder.WorkOrderServiceReadReplyDTO
import com.mincom.enterpriseservice.exception.EnterpriseServiceOperationException
import com.mincom.eql.Constraint
import com.mincom.eql.impl.QueryImpl
import groovy.sql.Sql
import javax.naming.InitialContext
import java.text.DecimalFormat

class RequisitionService_modifyItem extends ServiceHook {
    String hookVersion = "1"

    def lastN(String input, int n) {
        return n > input?.size() ? null : n ? input[-n..-1] : ''
    }
    def getWOProject(String wONo) {
        log.info("Get Project Number from Work Order...")
        String result
        try {
            WorkOrderServiceReadReplyDTO WKReadReply = tools.service.get('WorkOrder').read({
                it.districtCode = tools.commarea.District
                it.workOrder = wONo
            })
            result = WKReadReply.getProjectNo()
        }catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def getProjectNo(String item, String entityKey) {
        String result
        log.info("getProjectNo Operation Start...")
        log.info("item: $item")
        log.info("entityKey: $entityKey")
        try {
            File checkFile1 = new File("/var/opt/appliance/efs/work/PBC." + entityKey)
            def lines = checkFile1.readLines()
            if (lines.size() > 3){
                item = lastN(item, 3)
                log.info("item getProjectNo: $item")
                Integer resultLineNo = scanLine("/var/opt/appliance/efs/work/PBC.$entityKey", "ITM$item")
                log.info("Project found?: $resultLineNo")
                if (resultLineNo == 0){
                    if (checkFile1.readLines().get(2).substring(0).trim() != ""){
                        result = checkFile1.readLines().get(2).substring(9)
                    } else{
                        result = ""
                    }
                } else{
                    result = checkFile1.readLines().get(resultLineNo - 1).substring(9)
                }
            }
            else if (lines.size() == 2) {
                result = ""
            }
            else {
                result = checkFile1.readLines().get(2).substring(9)
            }
            log.info ("getProjectNo result : $result")
        } catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def getProjectHeader(String entityKey) {
        log.info("Get Project No From Header")

        String result
        try {
            File checkFile1 = new File("/var/opt/appliance/efs/work/PBC." + entityKey)
            def lines = checkFile1.readLines()
            if (lines.size() > 3){
                Integer resultLineNo = scanLine("/var/opt/appliance/efs/work/PBC." + entityKey, "3.PROJ :")
                log.info("Project found?: $resultLineNo")
                if (resultLineNo == 0){
                    if (checkFile1.readLines().get(2).substring(0).trim() != ""){
                        result = checkFile1.readLines().get(2).substring(9)
                    } else{
                        result = ""
                    }
                } else{
                    result = checkFile1.readLines().get(resultLineNo - 1).substring(9)
                }
            }
            else if (lines.size() == 2) {
                result = ""
            }
            else {
                result = checkFile1.readLines().get(2).substring(9)
            }
            log.info ("getProjectNo result : $result")
        } catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def getProjectItem(String districtCode, String itemNo, String reqNo) {
        log.info ("Getting Previous Project Item...")
        String result
        try {
            String getProject
            String getWorkOrder
            String strSql = "select project_no, work_order from msf232 " +
                    "where dstrct_code = '$districtCode' and (requisition_no like '$reqNo%0' || '$itemNo%' " +
                    "or requisition_no like '$reqNo%' || '$itemNo%') and alloc_count = '01'"

            log.info("strSql: $strSql")

            InitialContext initial = new InitialContext()
            Object CAISource = initial.lookup("java:jboss/datasources/ReadOnlyDatasource")
            def sql = new Sql(CAISource)
            def QueryRes1 = sql.firstRow(strSql)
            if (QueryRes1) {
                getProject = QueryRes1.PROJECT_NO.trim()
                getWorkOrder = QueryRes1.WORK_ORDER.trim()

                if (getWorkOrder != "" && getWorkOrder != null){
                    WorkOrderServiceReadReplyDTO workOrderServiceReadReplyDTO = tools.service.get('WorkOrder').read({
                        it.districtCode = tools.commarea.District
                        it.workOrder = getWorkOrder
                    })
                    result = workOrderServiceReadReplyDTO.getProjectNo()
                }else{
                    result = getProject
                }
            } else{
                strSql = "select project_no, work_order from msf232 " +
                        "where dstrct_code = '$districtCode' and (requisition_no like '$reqNo  0000%' " +
                        "or requisition_no like '$reqNo  000%') and alloc_count = '01'"

                log.info("strSql2: $strSql")
                def QueryRes2 = sql.firstRow(strSql);
                log.info ("Query Header Costing:" + strSql)
                if (QueryRes2){
                    getProject = QueryRes2.PROJECT_NO.trim()
                    getWorkOrder = QueryRes2.WORK_ORDER.trim()
                    log.info("getProjectNo-->: $getProject")
                    log.info("getWorkOrder-->: $getWorkOrder")
                    if (getWorkOrder != "" && getWorkOrder != null){
                        Constraint district = MSF620Key.dstrctCode.equalTo(districtCode)
                        Constraint workOrder = MSF620Key.workOrder.equalTo(getWorkOrder)

                        def q = new QueryImpl(MSF620Rec.class).and(district).and(workOrder)
                        MSF620Rec msf620Rec = tools.edoi.firstRow(q)

                        if (msf620Rec) {
                            result = msf620Rec.getProjectNo().trim()
                            log.info("result-->: $result")
                        } else {
                            result = getProject
                            log.info("resultP-->: $result")
                        }
                    }else{
                        result = getProject
                        log.info("resultAAAAA: $result")
                    }
                }
            }
            log.info ("Get PROJ Project : $result")
        } catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def setItemProject(String entityKey, String projectNo, String itemNo) {
        log.info ("SetItemProj......")
        try {
            File checkFile1 = new File("/var/opt/appliance/efs/work/PBC.$entityKey")
            def lines = checkFile1.readLines()
            log.info("Line Size : " + lines.size())
            Integer findLine
            if (lines.size() > 3){
                findLine = scanLine("/var/opt/appliance/efs/work/PBC." + entityKey, "ITM$itemNo")
                if (findLine == 0){
                    insertLineFile("\nITM$itemNo : $projectNo", entityKey)
                }else{
                    changeLineText("ITM$itemNo : $projectNo", findLine, entityKey)
                }
            }else if (lines.size() == 2){
                insertLineFile("\n3.PROJ : <none>", entityKey)
                insertLineFile("\nITM$itemNo : $projectNo", entityKey)
            }else{
                insertLineFile("\nITM$itemNo : $projectNo", entityKey)
            }
        }catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return ""
    }
    def scanLine(String path, String strFind){
        log.info("Scanning line to find $strFind ...")
        Integer result = 0
        try {
            File file = new File(path)
            Scanner scanner = new Scanner(file)
            Integer lineNum = 0
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                lineNum++;
                if(line.contains(strFind)) {
                    log.info ("Line Find " + lineNum)
                    result = lineNum
                }
            }
        } catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Erorr Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def getPreviousItem(String districtCode, String reqNo, String reqType, String projectNo) {
        log.info ("Get Previous Item Value...")
        BigDecimal result
        try {
            InitialContext initial = new InitialContext()
            Object CAISource = initial.lookup("java:jboss/datasources/ReadOnlyDatasource")
            def sql = new Sql(CAISource)

            log.info ("ReqType: $reqType")

            ArrayList itemData = new ArrayList()
            String strSQL
            if (reqType == "PR"){
                strSQL = "select PREQ_ITEM_NO,TOTAL_ITEM_VALUE from msf231 where dstrct_code = '$districtCode' and preq_no = '$reqNo'"
                sql.eachRow(strSQL, {
                    itemData.add(it.preq_item_no.toString().substring(it.preq_item_no.toString().length() - 3) + it.total_item_value)
                })
            } else{
                strSQL = "select a.ireq_item, " +
                        "case when a.invent_value = 0 then b.invent_cost_pr * a.qty_req else a.invent_value end invent_value " +
                        "from msf141 a " +
                        "left outer join msf170 b on (a.dstrct_code = b.dstrct_code and a.stock_code = b.stock_code) " +
                        "where a.dstrct_code = '$districtCode' and a.ireq_no = '$reqNo'"
                sql.eachRow(strSQL, {
                    itemData.add(it.ireq_item.toString().substring(it.ireq_item.toString().length() - 3) + it.invent_value)
                })
            }

            log.info ("StrSQL: " + strSQL)
            log.info ("Size of itemData: " + itemData.size())

            if (itemData.size() > 0){
                itemData.each { itemsData ->
                    String cekProjectNo = getProjectItem(districtCode, itemsData.toString().substring(0, 3), reqNo)
                    BigDecimal test = itemsData.toString().substring(3).toBigDecimal()
                    log.info("result****: $result")
                    log.info("cekProjectNo: $cekProjectNo")
                    log.info("projectNo: $projectNo")
                    log.info("test: $test")
                    if (cekProjectNo == projectNo){
                        if (!result) {
                            log.info("Result is null")
                            result = 0
                        }
                        result = result + itemsData.toString().substring(3).toBigDecimal()
                    }
                }
            } else {
                result = 0
            }
            log.info ("previousItemValue Result: $result")
        } catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def getTotalBudget(String districtCode, String projectNbr) {
        log.info("Get total budget of Project Nbr: " + projectNbr)
        BigDecimal result = 0
        if(projectNbr && projectNbr.trim() != "") {
            String projectType
            if(projectNbr.substring(2, 4) == "3Y"
                    || projectNbr.substring(2, 4) == "4A"
                    || projectNbr.substring(2, 4) == "4B") {
                projectType = "AI"
            }
            else if(projectNbr.substring(2, 4) == "2O"
                    || projectNbr.substring(2, 4) == "3O") {
                projectType = "AO"
            }
            else if(projectNbr.substring(3, 4) == "G"
                    || projectNbr.substring(3, 4) == "H"
                    || projectNbr.substring(3, 4) == "I"
                    || projectNbr.substring(3, 4) == "J"
                    || projectNbr.substring(3, 4) == "K"
                    || projectNbr.substring(3, 4) == "L"
                    || projectNbr.substring(3, 4) == "N"
                    || projectNbr.substring(3, 4) == "P") {
                projectType = "HAR"
            }
            else if(projectNbr.substring(3, 4) == "S") {
                projectType = "ADM"
            }
            else {
                projectType = null
            }

            try {
                InitialContext initialContext = new InitialContext()
                Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                def sql = new Sql(dataSource)
                def budgetInTotal
                if(projectType == "AI" || projectType == "AO" || projectType == "HAR") {
                    log.info("Query Command: " +
                            "SELECT TOT_EST_COST totEstCost " +
                            "FROM MSF667 " +
                            "WHERE CATEGORY_CODE = ' ' " +
                            "AND EXP_REV_IND = 'E' " +
                            "AND PROJECT_NO = '$projectNbr' " +
                            "AND DSTRCT_CODE = '$districtCode'")

                    budgetInTotal = sql.firstRow("SELECT TOT_EST_COST totEstCost " +
                            "FROM MSF667 " +
                            "WHERE CATEGORY_CODE = ' ' " +
                            "AND EXP_REV_IND = 'E' " +
                            "AND PROJECT_NO = '$projectNbr' " +
                            "AND DSTRCT_CODE = '$districtCode'")
                }
                else if(projectType == "ADM") {
                    projectNbr = projectNbr.substring(0, 4)
                    log.info("Query Command: " +
                            "SELECT TOT_EST_COST totEstCost " +
                            "FROM MSF667 " +
                            "WHERE CATEGORY_CODE = ' ' " +
                            "AND EXP_REV_IND = 'E' " +
                            "AND PROJECT_NO = '$projectNbr' " +
                            "AND DSTRCT_CODE = '$districtCode'")

                    budgetInTotal = sql.firstRow("SELECT TOT_EST_COST totEstCost " +
                            "FROM MSF667 " +
                            "WHERE CATEGORY_CODE = ' ' " +
                            "AND EXP_REV_IND = 'E' " +
                            "AND PROJECT_NO = '$projectNbr' " +
                            "AND DSTRCT_CODE = '$districtCode'")
                }
                else {
                    budgetInTotal = null
                }

                if(budgetInTotal){
                    String[] budget = budgetInTotal.totEstCost
                    result = budget[0].trim() as BigDecimal
                }
                else {
                    result = 0
                }
                log.info("Query result totalBudget: $result")
            } catch(EnterpriseServiceOperationException e) {
                List <ErrorMessageDTO> listError = e.getErrorMessages()
                listError.each{ErrorMessageDTO errorDTO ->
                    log.info ("Error Code: " + errorDTO.getCode())
                    log.info ("Error Message: " + errorDTO.getMessage())
                    log.info ("Error Fields: " + errorDTO.getFieldName())
                }
            }
        }
        return result
    }
    def getCommitmentAI(String districtCode, String projectNbr) {
        log.info("Get AI Commitment of Project Nbr: " + projectNbr)
        BigDecimal result = 0
        if (projectNbr) {
            try {
                InitialContext initialContext = new InitialContext()
                Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                def sql = new Sql(dataSource)
                log.info("AAAA Query command: " +
                        "SELECT COMMITMENTS " +
                        "FROM VPBC_COMMITMENT_AI " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                def aIcommitment = sql.firstRow("SELECT COMMITMENTS " +
                        "FROM VPBC_COMMITMENT_AI " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                if (aIcommitment) {
                    String[] aiCommit = aIcommitment.COMMITMENTS
                    result = aiCommit[0].trim() as BigDecimal
                } else {
                    result = 0
                }
                log.info("Query result commitmentAI: " + result)
            } catch(EnterpriseServiceOperationException e) {
                List <ErrorMessageDTO> listError = e.getErrorMessages()
                listError.each{ErrorMessageDTO errorDTO ->
                    log.info ("Error Code: " + errorDTO.getCode())
                    log.info ("Error Message: " + errorDTO.getMessage())
                    log.info ("Error Fields: " + errorDTO.getFieldName())
                }
            }
        }
        return result
    }
    def getCommitmentAO(String districtCode, String projectNbr) {
        log.info("Get AO Commitment of Project Nbr: " + projectNbr)
        BigDecimal result
        if(projectNbr) {
            try {
                InitialContext initialContext = new InitialContext()
                Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                def sql = new Sql(dataSource)
                log.info("Query command: " +
                        "SELECT COMMITMENTS " +
                        "FROM VPBC_COMMITMENT_AO " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                def aOCommitment = sql.firstRow("SELECT COMMITMENTS " +
                        "FROM VPBC_COMMITMENT_AO " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                if(aOCommitment){
                    String[] aoCommit = aOCommitment.COMMITMENTS
                    result =  aoCommit[0] as BigDecimal
                } else {
                    result = 0
                }
                log.info("Query result commitmentAO: " + result)
            } catch(EnterpriseServiceOperationException e) {
                List <ErrorMessageDTO> listError = e.getErrorMessages()
                listError.each{ErrorMessageDTO errorDTO ->
                    log.info ("Error Code: " + errorDTO.getCode())
                    log.info ("Error Message: " + errorDTO.getMessage())
                    log.info ("Error Fields: " + errorDTO.getFieldName())
                }
            }
        }
        return result
    }
    def getActualAI(String districtCode, String projectNbr) {
        log.info("Get AI Actuals of Project Nbr: " + projectNbr)
        BigDecimal result = 0
        if(projectNbr) {
            try {
                InitialContext initialContext = new InitialContext()
                Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                def sql = new Sql(dataSource)
                log.info("Query command: " +
                        "SELECT ACTUALS " +
                        "FROM VPBC_ACTUAL_AI " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                def aIActual = sql.firstRow("SELECT ACTUALS " +
                        "FROM VPBC_ACTUAL_AI " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                if(aIActual) {
                    String[] aiAct = aIActual.ACTUALS
                    result = aiAct[0] as BigDecimal
                } else {
                    result = 0
                }
                log.info("Query result actualAI: " + result)
            } catch(EnterpriseServiceOperationException e) {
                List <ErrorMessageDTO> listError = e.getErrorMessages()
                listError.each{ErrorMessageDTO errorDTO ->
                    log.info ("Error Code: " + errorDTO.getCode())
                    log.info ("Error Message: " + errorDTO.getMessage())
                    log.info ("Error Fields: " + errorDTO.getFieldName())
                }
            }
        }
        return result
    }
    def getActualAO(String districtCode, String projectNbr) {
        log.info("Get AO Actuals of Project Nbr: " + projectNbr)
        BigDecimal result
        if(projectNbr) {
            try {
                InitialContext initialContext = new InitialContext()
                Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                def sql = new Sql(dataSource)
                log.info("Query command: " +
                        "SELECT COMMITMENTS " +
                        "FROM VPBC_ACTUAL_AO " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                def aOActual = sql.firstRow("SELECT ACTUALS " +
                        "FROM VPBC_ACTUAL_AO " +
                        "WHERE DSTRCT_CODE = '" + districtCode +
                        "' AND PROJECT_NO = '" + projectNbr + "'")
                if(aOActual){
                    String[] aoAct = aOActual.ACTUALS
                    result = aoAct[0] as BigDecimal
                } else {
                    result = 0
                }
                log.info("Query result actualAO: " + result)
            } catch(EnterpriseServiceOperationException e) {
                List <ErrorMessageDTO> listError = e.getErrorMessages()
                listError.each{ErrorMessageDTO errorDTO ->
                    log.info ("Error Code: " + errorDTO.getCode())
                    log.info ("Error Message: " + errorDTO.getMessage())
                    log.info ("Error Fields: " + errorDTO.getFieldName())
                }
            }
        }
        return result
    }
    def checkZeroBudget(String districtCode, String projectNo) {
        log.info("Checking zero budget...")
        Boolean result
        BigDecimal totalBudget = getTotalBudget(districtCode, projectNo)

        log.info("totalBudget: $totalBudget")

        if(!totalBudget || totalBudget == 0) {
            result = true
        } else {
            result = false
        }
        log.info("Result from checkZeroBudget: $result")
        return result
    }
    def checkWarning(int ctr, String entityKey) {
        log.info("Check Warning Status...")
        Boolean result
        try {
            File fileToCheck = new File("/var/opt/appliance/efs/work/PBC." + entityKey)
            Integer findLine = scanLine("/var/opt/appliance/efs/work/PBC." + entityKey, "WARNING" + ctr.toString() + ":")
            log.info("FindLine file to check: " + findLine)
            if (findLine == null) return false
            if(findLine != 0) {
                log.info("masukctr: " + ctr)
                log.info("warnStatus: " + fileToCheck.readLines().get(findLine - 1).substring(9, 10).trim())
                String warnStatus = fileToCheck.readLines().get(findLine - 1).substring(9, 10).trim()
                if(warnStatus == "Y"){
                    result = true
                } else{
                    result = false
                }
            } else {
                result = false
            }
        } catch(EnterpriseServiceOperationException e) {
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def insertLineFile(String strLine, String entityKey) {
        try {
            File file = new File("/var/opt/appliance/efs/work/PBC." + entityKey)
            file << strLine
        } catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return
    }
    def changeLineText(String NewLine, int row, String entityKey) {
        try {
            log.info ("Change Line Text")
            String changeLine
            File file = new File("/var/opt/appliance/efs/work/PBC." + entityKey)
            changeLine = file.readLines().get(row - 1).substring(0)
            BufferedReader br = new BufferedReader(new FileReader("/var/opt/appliance/efs/work/PBC." + entityKey))
            String line
            String tmp = ""
            while ((line = br.readLine()) != null) tmp += line + '\n'
            br.close()

            tmp = tmp.replace(changeLine, NewLine)
            FileOutputStream fileOut = new FileOutputStream("/var/opt/appliance/efs/work/PBC." + entityKey)
            fileOut.write(tmp.getBytes())
            fileOut.close()
        }catch (EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Error Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return
    }
    def setWarning(int ctr, String entityKey) {
        log.info("Set Warning Status...")
        Boolean result = false
        try {
            Integer findLine = scanLine("/var/opt/appliance/efs/work/PBC." + entityKey,"WARNING" + ctr.toString() + ":")
            if(findLine != 0){
                changeLineText("WARNING" + ctr.toString() + ":" + "Y", findLine, entityKey)
            }
            else{
                insertLineFile("\nWARNING" + ctr.toString() + ":" + "Y", entityKey)
            }
            result = true
        } catch(EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Erorr Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
        return result
    }
    def setNoWarning(int ctr, String entityKey) {
        log.info("Set Warning Status...")
        try {
            Integer findLine = scanLine("/var/opt/appliance/efs/work/PBC." + entityKey,"WARNING" + ctr.toString() + ":")
            if(findLine != 0){
                changeLineText("WARNING" + ctr.toString() + ":" + "N", findLine, entityKey)
            }
            else{
                insertLineFile("\nWARNING" + ctr.toString() + ":" + "N", entityKey)
            }
        } catch(EnterpriseServiceOperationException e){
            List <ErrorMessageDTO> listError = e.getErrorMessages()
            listError.each{ErrorMessageDTO errorDTO ->
                log.info ("Erorr Code:" + errorDTO.getCode())
                log.info ("Error Message:" + errorDTO.getMessage())
                log.info ("Error Fields: " + errorDTO.getFieldName())
            }
        }
    }
    def validateBudget(String districtCode, String projectNo, BigDecimal tranValue, BigDecimal committedValue) {
        log.info("Calculating Budget ...")

        Boolean result
        BigDecimal totalBudget = getTotalBudget(districtCode, projectNo)
        BigDecimal commitmentAI = getCommitmentAI(districtCode, projectNo)
        BigDecimal commitmentAO = getCommitmentAO(districtCode, projectNo)
        BigDecimal actualAI = getActualAI(districtCode, projectNo)
        BigDecimal actualAO = getActualAO(districtCode, projectNo)

        if (!totalBudget || totalBudget == 0) {
            result = false
        } else{
            BigDecimal checkValue = (((tranValue + commitmentAO + commitmentAI + actualAO + actualAI - committedValue) / totalBudget) * 100).setScale(2, BigDecimal.ROUND_HALF_UP)
            BigDecimal pembilang = tranValue + commitmentAO + commitmentAI + actualAO + actualAI  - committedValue
            BigDecimal penyebut = totalBudget
            BigDecimal hasil = pembilang / penyebut
            BigDecimal hasil2 = hasil * 100

            log.info("Pembilangggg: $pembilang")
            log.info("Penyebutttt: $penyebut")
            log.info("hasil: $hasil")
            log.info("hasil2: $hasil2")
            log.info("committedValue: $committedValue")
            log.info("checkValue: $checkValue")

            if (checkValue > 100) {
                result = true
            } else {
                result = false
            }
        }
        return result
    }
    def getModuleSwitch(String tblType, String tblCode) {
        String result
        Constraint tableType = MSF010Key.tableType.equalTo(tblType)
        Constraint tableCode = MSF010Key.tableCode.equalTo(tblCode)

        def qMSF010 = new QueryImpl(MSF010Rec.class).and(tableType).and(tableCode)
        MSF010Rec msf010Rec = tools.edoi.firstRow(qMSF010)

        String assocRec
        if (msf010Rec) {
            assocRec = msf010Rec.getAssocRec().trim()
            if (assocRec != "") {
                result = assocRec.substring(0, 1)
            } else {
                result = ""
            }
        } else {
            result = ""
        }
        log.info("result: $result")
        return result
    }
    def getFinalisedDate(String districtCode, String projectNo) {
        log.info("Get Finalised Date ...")
        String result

        Constraint projectNbr = MSF660Key.projectNo.equalTo(projectNo)
        Constraint districtCD = MSF660Key.dstrctCode.equalTo(districtCode)

        def query = new QueryImpl(MSF660Rec.class).and(districtCD).and(projectNbr)
        MSF660Rec msf660Rec = tools.edoi.firstRow(query)

        if (msf660Rec) {
            String finalisedDate = msf660Rec.getFinalisedDate().trim()
            if (finalisedDate && finalisedDate != "") {
                result = "FINALISED"
            } else {
                result = ""
            }
        }
        else {
            result = ""
        }
        log.info("result Project Status: $result")
        return result
    }

    @Override
    Object onPreExecute(Object input){
        log.info("Hooks onPreExecute RequisitionService_modifyItem logging.version: $hookVersion")
        return null
    }

    @Override
    Object onPostExecute(Object input, Object result){
        log.info("[ARSIADI] Hooks onPostExecute RequisitionService_modifyItem logging.version: $hookVersion")

        String pBCActiveFlag = getModuleSwitch("+PBC", "MSE140")
        log.info("PBC Active Flag: $pBCActiveFlag")
        if (pBCActiveFlag != "" && pBCActiveFlag != "Y") {
            return null
        }

        DecimalFormat formatter = new DecimalFormat("#,###.00")

        RequisitionServiceModifyItemRequestDTO userInput = (RequisitionServiceModifyItemRequestDTO) input
        RequisitionServiceModifyItemReplyDTO userResult = (RequisitionServiceModifyItemReplyDTO) result

        String districtCode = userInput.getDistrictCode()
        String reqType = userInput.getIreqType()
        String pRNo = userInput.getPreqNo()
        String iRNo = userInput.getIreqNo()

        log.info("districtCode: $districtCode")
        log.info("reqType: $reqType")
        log.info("pRNo: $pRNo")
        log.info("iRNo: $iRNo")

        String reqNo
        String entityKey

        if (iRNo && iRNo.trim() != "") {
            reqNo = iRNo
            entityKey = districtCode + reqType + iRNo
        } else if (pRNo && pRNo.trim() != "") {
            reqNo = pRNo
            entityKey = districtCode + reqType + pRNo
        } else {
            return null
        }

        log.info("entityKey : " + entityKey)
        log.info("reqNo: $reqNo")

        RequisitionItemDTO[] reqItemDTO = userResult.requisitionItems
        String projectHeader = getProjectHeader(entityKey)

        log.info("ReqItemDTO.length : " + reqItemDTO.length)
        log.info("projectHeader: $projectHeader")

        String projectItem
        String workOrderItem
        for (int i = 0; i < reqItemDTO.length; i++) {
            projectItem = reqItemDTO[i].getProjectA().trim()
            workOrderItem = reqItemDTO[i].getWorkOrderA().toString().trim()

            if ((!projectItem || projectItem == "") && workOrderItem && workOrderItem != "") {
                projectItem = getWOProject(workOrderItem)
            } else {
                projectItem = getProjectHeader(entityKey)
            }

            log.info("projectItem: $projectItem")

            BigDecimal qtyRequired = reqItemDTO[i].getQuantityRequired() as BigDecimal
            BigDecimal estPrice = reqItemDTO[i].getEstimatedPrice() as BigDecimal
            String reqItemNo = lastN("000" + reqItemDTO[i].getIssueRequisitionItem() as String, 3)

            log.info("qtyRequired: $qtyRequired")
            log.info("estPrice: $estPrice")
            log.info("reqItemNo: $reqItemNo")
            log.info("MasukSiniGuys......................")

            log.info("projectItem: $projectItem")
            log.info("workOrderItem: $workOrderItem")
            log.info("i: $i")

            if ((!projectItem || projectItem == "") && workOrderItem && workOrderItem != "") {
                projectItem = getWOProject(reqItemDTO[i].getWorkOrderA().toString().trim())
            } else {
                log.info("fasdfsgdhfgdsfasdfdagshdsfdagshds")
                projectItem = getProjectNo(i.toString(), entityKey)
            }

            log.info("projectNo: $projectItem")

            BigDecimal itemValue
            String itemType = reqItemDTO[i].getItemType().trim()

            log.info("itemType: $itemType")

            if (itemType && itemType != "") {
                setItemProject(entityKey, projectItem, reqItemNo)

                BigDecimal prevItemVal = getPreviousItem(districtCode, reqNo, reqType, projectItem)
                log.info("prevItemVal: $prevItemVal")

                BigDecimal previousItemValue = 0
                for (int j = 0; j < i ; j++) {
                    log.info("projectItem---: $projectItem")
                    log.info("projectHeader---: $projectHeader")
                    previousItemValue = reqItemDTO[j].getItemValue() as BigDecimal

                    if (previousItemValue) {
                        if (projectItem == projectHeader) {
                            prevItemVal = prevItemVal + previousItemValue
                        }
                    } else {
                        previousItemValue = 0
                    }
                }

                BigDecimal tempItemValue
                BigDecimal inventCostPr
                if (itemType == "S") {
                    String stockCode = reqItemDTO[i].getStockCode()
                    String inventCat

                    if (reqItemDTO[i].getItemInventoryCategory()) {
                        inventCat = reqItemDTO[i].getItemInventoryCategory().trim()
                    }

                    String queryInventCostPr
                    if (inventCat) {
                        queryInventCostPr = "SELECT CASE WHEN B.INVENT_COST_PR IS NOT NULL THEN B.INVENT_COST_PR " +
                                "ELSE A.INVENT_COST_PR END AS INVENT_COST_PR " +
                                "FROM MSF170 a LEFT OUTER JOIN MSF194 b ON a.dstrct_code = b.dstrct_code AND a.stock_code = b.stock_code " +
                                "WHERE a.STOCK_CODE = '$stockCode' " +
                                "AND a.DSTRCT_CODE = '$districtCode' " +
                                "AND (B.INVENT_CAT = '$inventCat' OR B.INVENT_CAT IS NULL)"
                    } else {
                        queryInventCostPr = "SELECT INVENT_COST_PR " +
                                "FROM MSF170 " +
                                "WHERE STOCK_CODE = '$stockCode' " +
                                "AND DSTRCT_CODE = '$districtCode'"
                    }
                    log.info("queryInventCostPr: $queryInventCostPr")

                    log.info("Stock Code: $stockCode")
                    log.info("Invent Category: $inventCat")

                    InitialContext initialContext = new InitialContext()
                    Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                    def sql = new Sql(dataSource)
                    def queryResult = sql.firstRow(queryInventCostPr)
                    String[] resultInventCostPr = queryResult.INVENT_COST_PR

                    inventCostPr = resultInventCostPr[0].trim() as BigDecimal

                    log.info("inventCostPr: $inventCostPr")
                    log.info("qtyRequired: $qtyRequired")

                    tempItemValue = inventCostPr * qtyRequired
                } else {
                    tempItemValue = reqItemDTO[i].getItemValue() as BigDecimal
                }

                if (itemType == "V") {
                    tempItemValue = reqItemDTO[i].getEstimatedPrice() as BigDecimal
                }

                log.info("tempItemValue****: $tempItemValue")
                log.info("prevItemVal****: $prevItemVal")
                log.info("previousItemValue****: $previousItemValue")
                log.info("out Inventory Cost Price: $inventCostPr")

                itemValue = previousItemValue + tempItemValue
                log.info("itemValue: $itemValue")

                Boolean zeroBudget = checkZeroBudget(districtCode, projectItem)
                log.info("zeroBudget: $zeroBudget")

                if (zeroBudget) {
                    throw new EnterpriseServiceOperationException(
                            new ErrorMessageDTO(
                                    "9999",
                                    "PRK tidak bisa digunakan, karena tidak memiliki budget!",
                                    "ireqType",
                                    0,
                                    0
                            )
                    )
                    return input
                }

                InitialContext initialContext = new InitialContext()
                Object dataSource = initialContext.lookup("java:jboss/datasources/ReadOnlyDatasource")
                def sql = new Sql(dataSource)
                def queryResult

                BigDecimal committedValue
                if (reqType == "PR") {
                    queryResult = sql.firstRow("SELECT TOTAL_ITEM_VALUE FROM MSF231 WHERE DSTRCT_CODE = '$districtCode' " +
                            "AND PREQ_NO = '$reqNo' AND PREQ_ITEM_NO = '$reqItemNo'")

                    if (queryResult) {
                        String[] msf231Value = queryResult.TOTAL_ITEM_VALUE
                        committedValue = msf231Value[0].trim() as BigDecimal
                    } else {
                        committedValue = 0
                    }
                } else {
                    queryResult = sql.firstRow("SELECT QTY_REQ FROM MSF141 WHERE DSTRCT_CODE = '$districtCode' " +
                            "AND IREQ_NO = '$reqNo' " +
                            "AND IREQ_ITEM = '$reqItemNo'")


                    if (queryResult) {
                        String[] queryData = queryResult.QTY_REQ
                        BigDecimal qtyReqFromFile = queryData[0].trim() as BigDecimal
                        committedValue = qtyReqFromFile * inventCostPr
                    } else {
                        committedValue = 0
                    }
                }

                if (projectItem && projectItem != "") {
                    String finalisedDate = getFinalisedDate(districtCode, projectItem)
                    log.info("finalisedDate: $finalisedDate")
                    if (finalisedDate &&  finalisedDate != "") {
                        String errorMessage = "Transaksi tidak dapat dilakukan karena PRK sudah difinalisasi"
                        String errorCode = "9999"
                        throw new EnterpriseServiceOperationException(
                                new ErrorMessageDTO(
                                        errorCode,
                                        errorMessage,
                                        "ireqType",
                                        0,
                                        0
                                )
                        )
                        return input
                    }

                    Boolean overBudget = validateBudget(districtCode, projectItem, itemValue, committedValue)
                    log.info("overBudget: $overBudget")
                    if (overBudget) {
                        BigDecimal totalCommitment = getCommitmentAI(districtCode, projectItem) +
                                getCommitmentAO(districtCode, projectItem) - committedValue
                        BigDecimal totalActual = getActualAI(districtCode, projectItem) +
                                getActualAO(districtCode, projectItem)
                        BigDecimal totalBudget = getTotalBudget(districtCode, projectItem)
                        BigDecimal remainingBudget = totalBudget - totalActual - totalCommitment

                        log.info("Error Message Checkpoint...")
                        log.info("totalBudget: $totalBudget")
                        log.info("totalCommitment: $totalCommitment")
                        log.info("totalActual: $totalActual")
                        log.info("sisaAnggaran: $remainingBudget")
                        log.info("ProjNo: ---$projectItem---")
                        log.info("committedValue: $committedValue")
                        log.info("ProjNoLength: " + projectItem.length())
                        log.info("proj34: " + projectItem.substring(2, 4))
                        log.info("proj4: " + projectItem.substring(3, 4).trim())

                        if (projectItem.substring(2, 4) == "2O" || projectItem.substring(2, 4) == "3O") {
                            String totBudget = formatter.format(totalBudget)
                            String totActual = formatter.format(totalActual)
                            String totCommit = formatter.format(totalCommitment)
                            String remainBudget = formatter.format(remainingBudget)
                            String itmValue = formatter.format(itemValue)

                            String errorMessage = "Nilai transaksi overbudget: \n" +
                                    "Alokasi PRK: $totBudget\n " +
                                    "Realisasi PRK: $totActual\n" +
                                    "Commitment PRK: $totCommit\n" +
                                    "Sisa Anggaran PRK: $remainBudget\n" +
                                    "Nilai transaksi saat ini sebesar $itmValue lebih besar dari nilai anggaran\n" +
                                    "Segera ajukan revisi anggaran kepada divisi terkait"
                            String errorCode = "9999"

                            log.info("UserInput.getIreqType(): $reqType")

                            throw new EnterpriseServiceOperationException(
                                    new ErrorMessageDTO(
                                            errorCode,
                                            errorMessage,
                                            "ireqType",
                                            0,
                                            0
                                    )
                            )
                            return input
                        }

                        if(projectItem.substring(3, 4) == "G"
                                || projectItem.substring(3, 4) == "H"
                                || projectItem.substring(3, 4) == "I"
                                || projectItem.substring(3, 4) == "J"
                                || projectItem.substring(3, 4) == "K"
                                || projectItem.substring(3, 4) == "L"
                                || projectItem.substring(3, 4) == "N"
                                || projectItem.substring(3, 4) == "P"
                                || projectItem.substring(3, 4) == "S"
                        ) {
                            log.info("ARS Masuk Sini cooooy.....")
                            Boolean warning = checkWarning(i, entityKey)
                            log.info("SetWarning : $warning")

                            if (!warning) {
                                log.info("Warning nya belum muncul coooy, jadi munculin error nya")
                                Boolean warningSet = setWarning(i, entityKey)
                                double amount = Double.parseDouble(itemValue.toString())

                                if (warningSet) {
                                    String totBudget = formatter.format(totalBudget)
                                    String totActual = formatter.format(totalActual)
                                    String totCommit = formatter.format(totalCommitment)
                                    String remainBudget = formatter.format(remainingBudget)
                                    String itmValue = formatter.format(itemValue)

                                    String errorMessage = "WARNING! - Nilai transaksi overbudget:\r\n " +
                                            "Alokasi PRK: $totBudget\r\n " +
                                            "Realisasi PRK: $totActual\r\n " +
                                            "Commitment PRK: $totCommit\r\n " +
                                            "Sisa Anggaran PRK: $remainBudget\r\n " +
                                            "Nilai transaksi saat ini sebesar $itmValue lebih besar dari nilai anggaran\r\n " +
                                            "Segera ajukan revisi anggaran kepada divisi terkait (sebelum transaksi PO dan Issued diproses)."
                                    String errorCode = "9999"
                                    log.info("UserInput.getIreqType(): $reqType")

                                    throw new EnterpriseServiceOperationException(
                                            new ErrorMessageDTO(
                                                    errorCode,
                                                    errorMessage,
                                                    "ireqType",
                                                    0,
                                                    0)
                                    )
                                    return input
                                }
                                Boolean noWarning = setNoWarning(i, entityKey)
                                log.info("No Warning Switch: $noWarning")
                            }
                        }
                    }
                }
            } else {
                break
            }
        }
        return result
    }
}
