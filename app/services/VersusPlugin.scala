package services

import play.api.{ Plugin, Logger, Application }

import java.util.Date
import play.api.libs.json.Json
import play.api.Play.current
import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise
import java.io._
import models.FileMD
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._

import play.api.mvc._
import services._
import play.api.libs.concurrent.Execution.Implicits._

import play.api.libs.iteratee.Input.{El, EOF, Empty}
import com.mongodb.casbah.gridfs.GridFS

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import java.util.Date
import models.File
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._

import scala.concurrent.{future, blocking, Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global

import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef

/** 
 * Versus Plugin
 * 
 * @author Smruti
 * 
 **/
class VersusPlugin(application:Application) extends Plugin{
  
  val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val files: FileService =  DI.injector.getInstance(classOf[FileService])
  
  override def onStart() {
    Logger.debug("Starting Versus Plugin")
  }
  
   // Get all indexes from Versus
  def getIndexes():scala.concurrent.Future[play.api.libs.ws.Response]={  
    val configuration = play.api.Play.configuration
    val host=configuration.getString("versus.host").getOrElse("")
    val indexurl=host+"/index"
    var k=0
    val indexList:scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(indexurl).get()
    indexList.map{
      response=>Logger.debug("GETINDEXES: response.body="+response.body)
    }
       indexList
  }
  //index your file
  def index(id:String, fileType:String){
    
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId=configuration.getString("versus.index").getOrElse("")
   // val urlf= client+"/files/"+id+"/blob"
    val urlf=client+"/api/files/"+id+"?key=letmein"
    val host=configuration.getString("versus.host").getOrElse("")
    
     var indexurl=host+"/index/"+indexId+"/add"
     
     val indexList=getIndexes()
     var k=0
      indexList.map{
	    	response=> 
	    	Logger.debug("response.body="+response.body)
	    	val json: JsValue=Json.parse(response.body)
	    	Logger.debug("index(): json="+json);
	    	val list=json.as[Seq[models.IndexList.IndexList]]
	    	val len=list.length
	    	val indexes=new Array[(String,String)](len)
	    	
	    	  list.map{
	    	  index=>Logger.debug("indexID="+index.indexID+" MIMEType="+index.MIMEtype);
	    	  indexes.update(k, (index.indexID,index.MIMEtype))
	    	  if(fileType.contains(index.MIMEtype)) {
	    	     indexurl=host+"/index/"+index.indexID+"/add"
	    	    		WS.url(indexurl).post(Map("infile" -> Seq(urlf))).map{
	    			res=> 
	    			  Logger.debug("res.body="+res.body)
	        		      		
	    		  }//WS map end
	    	   }//if fileType end
	    	}
	    	
     }
    
  }
  
   def build(){
    val configuration = play.api.Play.configuration
    val indexId=configuration.getString("versus.index").getOrElse("")
    val host=configuration.getString("versus.host").getOrElse("")
    val buildurl=host+"/index/"+indexId+"/build"
        
    WS.url(buildurl).get().map{
    	r=>Logger.debug("r.body"+r.body);
     }
    
  }
  
//  def query(id:String):scala.concurrent.Future[play.api.libs.ws.Response]= {
   
  def query(id:String):scala.concurrent.Future[Array[(String,String,Double,String)]]={
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
    val indexId=configuration.getString("versus.index").getOrElse("")
    //val query= client+"/queries/"+id+"/blob"
      val query=client+"/api/queries/"+id+"?key=letmein"
    val host=configuration.getString("versus.host").getOrElse("")
      
    val queryurl=host+"/index/"+indexId+"/query" 
   
    
        val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(queryurl).post(Map("infile" -> Seq(query)))
               
        resultFuture.map{
	        res=>
		        val json: JsValue=Json.parse(res.body)
		        val simvalue=json.as[Seq[models.Result.Result]]
		        
		        val l=simvalue.length
		        val ar=new Array[String](l)
		        val se=new Array[(String,String,Double,String)](l)
		        var i=0
		        simvalue.map{
		        	 s=>
		        	  val a=s.docID.split("/")
		        	  val n=a.length-2
		        	  files.getFile(a(n)) match{
		        	  case Some(file)=>{
		        	    se.update(i,(a(n),s.docID,s.proximity,file.filename))
		        	    ar.update(i, file.filename)
		        	    Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
		        	    i=i+1
		        	   }
		        	 case None=>None
		        		         
		        	 }
		        		         		    
		        }
	          se
          }
        	 
     }
 
  
  import scala.collection.mutable.ArrayBuffer
//query a specific index 
  
  def queryIndex(id:String,indxId:String):scala.concurrent.Future[(String,scala.collection.mutable.ArrayBuffer[(String,String,Double,String)])]={
    val configuration = play.api.Play.configuration
    val client = configuration.getString("versus.client").getOrElse("")
   // val indexId=configuration.getString("versus.index").getOrElse("")
    val indexId=indxId
   // val query= client+"/queries/"+id+"/blob"
      val query=client+"/api/queries/"+id+"?key=letmein"
    val host=configuration.getString("versus.host").getOrElse("")
      
    var queryurl=host+"/index/"+indexId+"/query" 
    
    val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(queryurl).post(Map("infile" -> Seq(query)))
               
   resultFuture.map{
   response=>
		        val json: JsValue=Json.parse(response.body)
		        val similarity_value=json.as[Seq[models.Result.Result]]
		        
		        
		        val len=similarity_value.length
		        val ar=new Array[String](len)
		        val se=new Array[(String,String,Double,String)](len)
		       
		        var resultArray=new ArrayBuffer[(String,String,Double,String)]()
		        
		        var i=0
		        similarity_value.map{
		        	 result=>
		        	   val end=result.docID.lastIndexOf("?")
		        	   val begin=result.docID.lastIndexOf("/");
		        	   val subStr=result.docID.substring(begin+1, end);
		        	  val a=result.docID.split("/")
		        	  val n=a.length-2
		        	  files.getFile(subStr) match{
		        	     
		        	  case Some(file)=>{
		        	   // se.update(i,(a(n),result.docID,result.proximity,file.filename))
		        	    resultArray+=((subStr,result.docID,result.proximity,file.filename))
		        	    ar.update(i, file.filename)
		        	    //Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
		        	    Logger.debug("resultArray=("+subStr+", "+result.proximity+", "+file.filename+")\n" )
		        	    i=i+1
		        	   }
		        	 case None=>None
		        		         
		        	 }
		        		         		    
		        } // End of similarity map
	          //se
		        (indexId,resultArray)
          }
 }
  
 def queryURL(url:String):scala.concurrent.Future[Array[(String,String,Double,String)]]={
    val configuration = play.api.Play.configuration
   // val client = configuration.getString("versus.client").getOrElse("")
    val indexId=configuration.getString("versus.index").getOrElse("")
    val query= url
    val host=configuration.getString("versus.host").getOrElse("")
    val queryurl=host+"/index/"+indexId+"/query" 
   
    
        val resultFuture: scala.concurrent.Future[play.api.libs.ws.Response]= WS.url(queryurl).post(Map("infile" -> Seq(query)))
               
        resultFuture.map{
	        res=>
		        val json: JsValue=Json.parse(res.body)
		        val simvalue=json.as[Seq[models.Result.Result]]
		        
		        val l=simvalue.length
		        val ar=new Array[String](l)
		        val se=new Array[(String,String,Double,String)](l)
		        var i=0
		        simvalue.map{
		        	 s=>
		        	  val a=s.docID.split("/")
		        	  val n=a.length-2
		        	  files.getFile(a(n)) match{
		        	  case Some(file)=>{
		        	    se.update(i,(a(n),s.docID,s.proximity,file.filename))
		        	    ar.update(i, file.filename)
		        	    Logger.debug("i"+i +" name="+ar(i)+"se(i)"+se(i)._3)
		        	    i=i+1
		        	   }
		        	 case None=>None
		        		         
		        	 }
		        		         		    
		        }
	          se
          }
        	 
     }
  
  
  override def onStop(){
    Logger.debug("Shutting down Versus Plugin")
  }
}

