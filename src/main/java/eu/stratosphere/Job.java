package eu.stratosphere;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;

import com.mongodb.BasicDBObject;
import com.mongodb.hadoop.io.BSONWritable;
import com.mongodb.hadoop.mapred.MongoInputFormat;

import eu.stratosphere.api.common.Plan;
import eu.stratosphere.api.common.Program;
import eu.stratosphere.api.common.operators.FileDataSink;
import eu.stratosphere.api.java.record.functions.MapFunction;
import eu.stratosphere.api.java.record.functions.ReduceFunction;
import eu.stratosphere.api.java.record.io.CsvOutputFormat;
import eu.stratosphere.api.java.record.operators.MapOperator;
import eu.stratosphere.api.java.record.operators.ReduceOperator;
import eu.stratosphere.client.LocalExecutor;
import eu.stratosphere.hadoopcompat.HadoopDataSource;
import eu.stratosphere.hadoopcompat.datatypes.WritableWrapper;
import eu.stratosphere.hadoopcompat.datatypes.WritableWrapperConverter;
import eu.stratosphere.nephele.client.JobExecutionResult;
import eu.stratosphere.types.IntValue;
import eu.stratosphere.types.LongValue;
import eu.stratosphere.types.Record;
import eu.stratosphere.types.StringValue;
import eu.stratosphere.util.Collector;



/**
 * This sample Stratosphere MongoDB job will
 * count the number of emails per day.
 */
public class Job implements Program {

	
	public static class ExtractDayIntoKey extends MapFunction {

		IntValue day = new IntValue();
		LongValue jodaDay = new LongValue();
		@Override
		public void map(Record record, Collector<Record> out) throws Exception {
			Writable valWr = record.getField(1, WritableWrapper.class).value();
			BSONWritable value = (BSONWritable) valWr;
			Object headers = value.getDoc().get("headers");
			BasicDBObject headerOb = (BasicDBObject) headers;
			String date = (String) headerOb.get("Date");
			DateFormat df = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss Z");
			try {
				Date parsed = df.parse(date);
				// represent each day as 20071107 (integer) for 2007.11.07
				day.setValue(Integer.parseInt(parsed.getYear()+""+parsed.getDay()+""+parsed.getMonth()));
				jodaDay.setValue(parsed.getTime());
				record.setField(0, day);
				record.setField(1, jodaDay);
				out.collect(record);
			} catch(ParseException p) {}
		}
	}
	
	public static class Count extends ReduceFunction {

		IntValue sInt = new IntValue();
		@Override
		public void reduce(Iterator<Record> records, Collector<Record> out)
				throws Exception {
			int cnt = 0;
			Record first = null;
			while(records.hasNext()) {
				cnt++;
				if(first == null) {
					first = records.next().createCopy();
				} else {
					records.next();
				}
			}
			sInt.setValue(cnt);
			first.setField(0, sInt);
			Date date = new Date(first.getField(1, LongValue.class).getValue());
			first.setField(1, new StringValue(date.toString()));
			out.collect(first);
		}
	}
	
	public Plan getPlan(String... args) {
    	
    	JobConf conf = new JobConf();
    	conf.set("mongo.input.uri","mongodb://localhost:27017/enron_mail.messages");
		HadoopDataSource src = new HadoopDataSource(new MongoInputFormat(), conf, "read from Mongodb", new WritableWrapperConverter());
    	
		MapOperator peekInto = MapOperator.builder(ExtractDayIntoKey.class )
			.input(src).build();
		
		ReduceOperator sortTest = ReduceOperator.builder(Count.class, IntValue.class, 0)
				.input(peekInto).build();
		
		FileDataSink sink = new FileDataSink(CsvOutputFormat.class, "file:///tmp/enronCountByDay");
        CsvOutputFormat.configureRecordFormat(sink)
        	.fieldDelimiter(',')
        	.recordDelimiter('\n')
        	.field(IntValue.class, 0)
        	.field(StringValue.class, 1);
        sink.setInput(sortTest);
        
        Plan p = new Plan(sink, "Stratosphere Quickstart SDK Sample Job");
        p.setDefaultParallelism(8);
		return p;
    }
    
    public String getDescription() {
        return "Usage: ... "; // TODO
    }

    // You can run this using:
    // mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath eu.stratosphere.RunJob <args>"
    public static void main(String[] args) throws Exception {
        Job tut = new Job();
        Plan toExecute = tut.getPlan(args);

        JobExecutionResult result = LocalExecutor.execute(toExecute);
        System.out.println("runtime:  " + result.getNetRuntime());
        System.exit(0);
    }
}