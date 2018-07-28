package ppthv;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class PrePostTransfusionHbValues {

    public static class Patient {
        public String name;
        public String empi;
        public String mrn;
        List<Transfusion> transfusions = new ArrayList<>();
        List<Hemoglobin> hemoglobins = new ArrayList<>();
    }
    
    public static class Transfusion implements Comparable {
        @Override
        public int compareTo(Object t) {
            return(this.serviceDay.compareTo(((Transfusion)t).serviceDay));
        }
        public Date serviceDay;
        public Double quantity;
        public Hemoglobin likelyPreTranfusionHb;
        public Hemoglobin likelyPostTranfusionHb;
    }
    
    public static class Hemoglobin implements Comparable {
        @Override
        public int compareTo(Object t) {
            return(this.verifyTime.compareTo(((Hemoglobin)t).verifyTime));
        }
        public Date verifyTime;
        public Double avgLabResult;
        public Boolean likelyPostTransfusionBump;
    }

    public static void main(String[] args) throws IOException, InvalidFormatException, ParseException {

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat stf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        
        List<Patient> patients = new ArrayList<>();
        
        // load from spreadsheet
        {
            Workbook workbook = WorkbookFactory.create(new File(args[0]));
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.rowIterator();
            String section = null;
            Patient patient = null;
            while(rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if("Patient".equals(row.getCell(0).getStringCellValue())) {
                    section = "Transfusions";
                    patient = null;
                }
                else if("MRN".equals(row.getCell(0).getStringCellValue())) {
                    section = "Hemoglobins";
                }
                else if("Transfusions".equals(section) && row.getCell(2).getStringCellValue() != null && !"".equals(row.getCell(2).getStringCellValue())) {
                    if(patient == null) {
                        patient = new Patient();
                        patient.name = row.getCell(0).getStringCellValue();
                        patients.add(patient);
                    }
                    Transfusion transfusion = new Transfusion();
                    transfusion.serviceDay = sdf.parse(row.getCell(2).getStringCellValue());
                    transfusion.quantity = new Double(row.getCell(6).getNumericCellValue());
                    patient.transfusions.add(transfusion);
                    Collections.sort(patient.transfusions);
                }
                else if("Hemoglobins".equals(section) && row.getCell(2).getStringCellValue() != null && !"".equals(row.getCell(2).getStringCellValue())) {
                    if(row.getCell(0).getStringCellValue() != null && !"".equals(row.getCell(0).getStringCellValue())) {
                        patient.mrn = row.getCell(0).getStringCellValue();
                    }
                    Hemoglobin hemoglobin = new Hemoglobin();
                    hemoglobin.verifyTime = stf.parse(row.getCell(2).getStringCellValue());
                    hemoglobin.avgLabResult = new Double(row.getCell(4).getNumericCellValue());
                    patient.hemoglobins.add(hemoglobin);
                    Collections.sort(patient.hemoglobins);
                }
            }
        }

        // look for possible post-transfusion hemoglobin bumps
        for(Patient patient : patients) {
            for(int x = 1; x < patient.hemoglobins.size(); x++) {
                patient.hemoglobins.get(x).likelyPostTransfusionBump = (patient.hemoglobins.get(x).avgLabResult - patient.hemoglobins.get(x - 1).avgLabResult > 0.5);
            }
        }
        
        // find the most likely pre and post transfusion hemoglobin for each transfusion
        for(Patient patient : patients) {
            for(Transfusion transfusion : patient.transfusions) {
                for(int x = 1; x < patient.hemoglobins.size(); x++) {
                    if(
                        patient.hemoglobins.get(x).likelyPostTransfusionBump
                        && DateUtils.truncate(patient.hemoglobins.get(x - 1).verifyTime, Calendar.DATE).compareTo(transfusion.serviceDay) <= 0
                        && DateUtils.truncate(patient.hemoglobins.get(x).verifyTime, Calendar.DATE).compareTo(transfusion.serviceDay) >= 0
                    ) {
                        transfusion.likelyPreTranfusionHb = patient.hemoglobins.get(x - 1);
                        transfusion.likelyPostTranfusionHb = patient.hemoglobins.get(x);
                        break;
                    }
                }
            }
        }

        // results to stdout
        System.out.printf("%s,%s,%s,%s,%s,%s,%s,%s",
            "name",
            "mrn",
            "transfusion day",
            "quantity",
            "pre-transfusion Hb",
            "",
            "post-transfusion Hb",
            ""
        );
        System.out.println();
        for(Patient patient : patients) {
            System.out.println();
            for(Transfusion transfusion : patient.transfusions) {
                System.out.printf("\"%s\",%s,%s,%2.0f,%s,%3.1f,%s,%3.1f",
                    patient.name,
                    patient.mrn,
                    sdf.format(transfusion.serviceDay),
                    transfusion.quantity,
                    (transfusion.likelyPreTranfusionHb != null ? stf.format(transfusion.likelyPreTranfusionHb.verifyTime) : "?"),
                    (transfusion.likelyPreTranfusionHb != null ? transfusion.likelyPreTranfusionHb.avgLabResult : null),
                    (transfusion.likelyPostTranfusionHb != null ? stf.format(transfusion.likelyPostTranfusionHb.verifyTime) : "?"),
                    (transfusion.likelyPreTranfusionHb != null ? transfusion.likelyPostTranfusionHb.avgLabResult : null)
                );
                System.out.println();
            }
            int hemoglobinIndex = 1;
            for(Hemoglobin hemoglobin : patient.hemoglobins) {
                System.out.printf(",hemoglobin,%d,%s,%s,%s",
                    hemoglobinIndex++,
                    stf.format(hemoglobin.verifyTime),
                    hemoglobin.avgLabResult,
                    (hemoglobin.likelyPostTransfusionBump != null && hemoglobin.likelyPostTransfusionBump ? "candidate post-transfusion value (>= 0.5 bump from previous)" : "")
                );
                System.out.println();
            }
        }
        
    }
    
}
