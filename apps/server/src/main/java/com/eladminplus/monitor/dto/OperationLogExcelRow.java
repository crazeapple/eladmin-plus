package com.eladminplus.monitor.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/** 操作日志导出行：列名中文直出，参数列放宽列宽；时间列格式化文本入库。 */
@Data
@ColumnWidth(20)
public class OperationLogExcelRow {

    @ExcelProperty("操作人")
    private String username;

    @ExcelProperty("IP")
    private String ip;

    @ExcelProperty("操作描述")
    private String description;

    @ExcelProperty("耗时（毫秒）")
    private Long elapsedMs;

    @ColumnWidth(50)
    @ExcelProperty("请求参数")
    private String requestParam;

    @ExcelProperty("操作时间")
    private String createdAt;
}
