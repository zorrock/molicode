package com.shareyi.molicode.handler.model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.shareyi.fileutil.FileUtil
import com.shareyi.molicode.common.chain.handler.SimpleHandler
import com.shareyi.molicode.common.chain.handler.awares.TableModelHandlerAware
import com.shareyi.molicode.common.constants.CommonConstant
import com.shareyi.molicode.common.utils.Profiles
import com.shareyi.molicode.common.utils.PubUtils
import com.shareyi.molicode.common.vo.code.ColumnVo
import com.shareyi.molicode.common.vo.code.TableDefineVo
import com.shareyi.molicode.common.vo.code.TableModelVo
import com.shareyi.molicode.common.vo.page.TableModelPageVo
import com.shareyi.molicode.context.TableModelContext
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Service

/**
 * tableModel 输出为json文件处理器
 *
 * @author zhangshibin
 * @since 2018/10/7
 */
@Service
class TableModelJsonOutputHandler extends SimpleHandler<TableModelContext> implements TableModelHandlerAware {
    @Override
    int getOrder() {
        return 5;
    }

    @Override
    boolean shouldHandle(TableModelContext tableModelContext) {
        return Objects.equals(tableModelContext.tableModelPageVo.modelType, CommonConstant.MODEL_TYPE_JSON);
    }

    @Override
    void doHandle(TableModelContext tableModelContext) {
        TableModelVo tableModelVo = tableModelContext.tableModelVo;
        TableDefineVo tableDefineVo = tableModelVo.tableDefine;
        TableModelPageVo tableModelPageVo = tableModelContext.tableModelPageVo;
        File f = new File(FileUtil.contactPath(tableModelPageVo.tableModelDir, tableDefineVo.dbTableName?.trim() + ".json"));

        //如果文件已经存在，需要读取，并将已配置的信息配置回来
        if (f.exists()) {
            loadFromPreConfigInfo(f, tableModelVo);
        }


        FileUtil.makeSureFileExsit(f);
        //换行符号替换为Windows的换行符号
        f.withWriter("utf-8")
                { writer ->
                    writer.write JSON.toJSONString(tableModelVo, SerializerFeature.PrettyFormat, SerializerFeature.QuoteFieldNames, SerializerFeature.UseSingleQuotes, SerializerFeature.DisableCircularReferenceDetect);
                }

        print "table " + tableModelPageVo.tableName + "的json文件成功生成，在：" + f.absolutePath;
        tableModelContext.setOutputPath(f.absolutePath)
    }

    /**
     * 从之前的配置信息中获取配置信息
     *
     * @param file
     * @param tableModelVo
     */
    void loadFromPreConfigInfo(File file, TableModelVo tableModelVo) {
        String preConfig = null;
        def inputStream = new FileInputStream(file)
        try {
            preConfig = IOUtils.toString(inputStream, Profiles.instance.fileEncoding);
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
        if (StringUtils.isBlank(preConfig)) {
            return;
        }

        List<String> allColumnList = Lists.newArrayList();
        Set<String> columnSet = Sets.newHashSet()
        tableModelVo.tableDefine.columns.each {
            column ->
                columnSet.add(column.getColumnName())
                allColumnList.add(column.getColumnName())
        }
        TableModelVo preTableModelVo = JSON.parseObject(preConfig, TableModelVo.class);
        tableModelVo.orderColumns = preTableModelVo.orderColumns;
        tableModelVo.bizFieldsMap = preTableModelVo.bizFieldsMap;
        tableModelVo.dictMap = preTableModelVo.dictMap;
        for (Map.Entry<String, String> entry : tableModelVo.bizFieldsMap) {
            String key = entry.getKey();
            if (Objects.equals(key, "allColumn")) {
                entry.setValue(StringUtils.join(allColumnList, ","));
            } else {
                entry.setValue(this.filterColumn(entry.getValue(), columnSet));
            }
        }

        tableModelVo.tableDefine.id = preTableModelVo.tableDefine.id;
        tableModelVo.tableDefine.cnname = preTableModelVo.tableDefine.cnname;
        tableModelVo.tableDefine.pageSize = preTableModelVo.tableDefine.pageSize;
        tableModelVo.tableDefine.isPaged = preTableModelVo.tableDefine.isPaged;
        tableModelVo.customProps = preTableModelVo.customProps;

        preTableModelVo.tableDefine.columns.each { preColumn ->
            ColumnVo columnVo = tableModelVo.tableDefine.getColumnByColumnName(preColumn.columnName);
            if (columnVo != null) {
                columnVo.setCnname(preColumn.cnname)
                columnVo.setCanBeNull(preColumn.canBeNull)
                columnVo.setJspTag(preColumn.jspTag)
                columnVo.setReadonly(preColumn.readonly)
                columnVo.setCanBeNull(preColumn.canBeNull)
                columnVo.setCustomProps(preColumn.customProps)
            }
        }
    }

    /**
     * 过滤字段，必须在新的数据库也存在
     * @param columnNames
     * @param allColumnSet
     * @return
     */
    String filterColumn(String columnNames, HashSet<String> allColumnSet) {
        List<String> columnNameList = PubUtils.stringToList(columnNames);
        List<String> filteredList = Lists.newArrayList();
        columnNameList.each {name->
            if(allColumnSet.contains(name)){
                filteredList.add(name);
            }
        }
        return StringUtils.join(filteredList, ",");
    }
}