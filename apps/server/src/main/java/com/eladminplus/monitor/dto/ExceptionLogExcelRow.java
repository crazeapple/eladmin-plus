package com.eladminplus.monitor.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/** 异常日志导出行：列名中文直出，堆栈列放宽列宽；时间列格式化文本入库。 */
@Data
@ColumnWidth(20)
public class ExceptionLogExcelRow {

    @ExcelProperty("操作人")
    private String username;

    @ExcelProperty("IP")
    private String ip;

    @ExcelProperty("异常类名")
    private String name;

    @ColumnWidth(40)
    @ExcelProperty("异常消息")
    private String message;

    @ColumnWidth(60)
    @ExcelProperty("堆栈")
    private String stackTrace;

    @ExcelProperty("异常时间")
    private String createdAt;
}
