import akka.actor.{Actor, ActorRef, Props, ActorSystem, AddressFromURIString}
import scala.util.control.Breaks
import scala.collection.mutable.ArrayBuffer
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.InetAddress
import java.security.MessageDigest
import akka.routing.RoundRobinRouter

case object Count
case class WorkProcess(end : Int, inc : Int, start: Long) 
case class StartWork(startime: Long, bossRef: ActorRef, k:Int)
case class MiningCoins(bitcoin : String, shacoin: String) 
case class WorkFinish(finish: Long)

class BitCoinRemote (future: scala.concurrent.Future[akka.actor.ActorRef], remoteSystem: ActorSystem) extends Actor{
var server: ActorRef = _
    
   future.onComplete {
      case Success(value) => 
       value ! "Connected"
       server = value
       println("Connected")
      case Failure(e) => 
	    e.printStackTrace
   }
     
  def receive = {
    case StartWork(startime, serverMaster,k) =>
      println(serverMaster)
      val boss = remoteSystem.actorOf(Props(new Boss(k,4, self, startime, serverMaster)), "Boss")
      boss ! Count 
     
    case WorkFinish(finish) =>
      context.stop(self)
      context.system.shutdown
      System.exit(0)
   }
  
 }
class Boss(k: Int, numberofactors: Int, bitCoin: ActorRef, startime: Long, serverMaster: ActorRef) extends Actor{
  var numberofresults: Int = _
  var noworkerfinish: Int = 0
  var listPoints:Map[String,String] = Map()
  val starttime: Long = System.currentTimeMillis
  val worker = context.actorOf(
  Props[Worker].withRouter(RoundRobinRouter(numberofactors)), name = "worker")
  
  def receive = {
    
   case Count =>    
     println("Bitcoin mining started at remote...")
     for(i <- 1 to numberofactors)
         {
    	   worker ! WorkProcess(k,i+5, starttime)
         }
   case MiningCoins(prefix, shamsg)  =>
     serverMaster ! MiningCoins(prefix, shamsg)
      	
   case WorkFinish  => 
     noworkerfinish+= 1
	   if(noworkerfinish == numberofactors){
       bitCoin ! WorkFinish
       var totletime: Long = (System.currentTimeMillis() - startime)
       println("\nTime taken by the CPU : "+totletime+" ms")
       println("Finished")
	     context.stop(self)
	     context.system.shutdown()
	   }
      		
  }
}
class Worker extends Actor {
  var counter=0
  private val sha = MessageDigest.getInstance("SHA-256")
  
  def searchCoins(k:Int, j:Int, start: Long)=
   {
     while(System.currentTimeMillis() - start <=30000)
     { 
    	var zeros=""
    	for(b<-1 to k)
    	{
    		zeros=zeros+"0"
    	}
    	for(a<-1 to 500)
    	{ 
    		var prefix="zhengfengbo"+scala.util.Random.alphanumeric.take(4+j).mkString+" "
    		var shamsg= hex_digest(prefix)
        counter=counter+1;
    		if(shamsg.startsWith(zeros)==true)
    		{
    			sender ! MiningCoins(prefix, shamsg)   			
    		}
    	}
     }
   }
    
   def hex_digest(s: String): String = {
    sha.digest(s.getBytes)
    .foldLeft("")((s: String, b: Byte) => s +
                  Character.forDigit((b & 0xf0) >> 4, 16) +
                  Character.forDigit(b & 0x0f, 16))
   }
  
  
  def receive = {
    case WorkProcess(k, j, start) =>
      searchCoins(k,j, start)
      sender ! WorkFinish
      context.stop(self)
  }
}

  
object BitCoinRemote extends App{
  override def main(args: Array[String]): Unit={
  
  val hostIP = InetAddress.getLocalHost.getHostAddress()
	println(hostIP)
	val RemoteIP:String=args(0)
	println("The remote IP address is:"+RemoteIP)
	println("Remote="+RemoteIP)
	
    val config = ConfigFactory.parseString("""
    akka {
       actor {
           provider = "akka.remote.RemoteActorRefProvider"
             }
       remote {
           enabled-transports = ["akka.remote.netty.tcp"]
       netty.tcp {
           hostname = """ + hostIP + """
           port = 0
                 }
             }
        }
   """) 
   
    val system = ActorSystem("Bitcoins", ConfigFactory.load(config))
    val duration = Duration(90000, SECONDS)
    val future = system.actorSelection("akka.tcp://bitcoinserver@"+RemoteIP+":5150/user/bitcoin").resolveOne(duration)
    val bitcoinremote = system.actorOf(Props(new BitCoinRemote(future, system)), "bitcoinremote")

  }
}