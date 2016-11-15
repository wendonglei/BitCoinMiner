import akka.actor.{ Actor, ActorRef, Props, ActorSystem, actorRef2Scala}
import java.net.InetAddress
import java.security.MessageDigest
import com.typesafe.config.ConfigFactory
import akka.routing.RoundRobinRouter

case object Count
case class WorkerProcess(end : Int, inc : Int, start: Long)
case class StartWork(startime: Long, bossRef: ActorRef,k:Int)
case class MiningCoins(bitcoin: String, shacoin: String) 
case class WorkFinish(finish: Long) 
case class AllFinish(finishtime: Long)

class BitCoin(bitcoinserver: ActorSystem,k:Int) extends Actor{
    val startime: Long = System.currentTimeMillis
    val boss = bitcoinserver.actorOf(Props(new Boss(k, 4, self, startime)), "Boss")
    boss ! Count 
    
    var numberofcoins: Int = _
    def receive = {
      
      case "Connected" =>
      println("Connection Established")
      println(sender)
      sender ! StartWork(startime, boss, k)
      
      case MiningCoins(bitcoin, shacoin) =>
      print( "Bitcoin= " + bitcoin )
      println("   sha256= " + shacoin)
      numberofcoins+=1
      
      case AllFinish(finishtime) =>
      var totletime: Long = (System.currentTimeMillis() - startime)
	  println("\nCPU time: "+finishtime+" ms")
      println("\nReal time : "+totletime+" ms")
      println("number of bitcoins:" + numberofcoins)
	    context.stop(self)
		context.system.shutdown
	    System.exit(0)
      case _ => 
   }
 
  }

class Boss(k: Int, numberofactors: Int, bitCoin: ActorRef, startime: Long) extends Actor {
  var numberofresults: Int = _
  var noworkerfinish: Int = 0
  var cputime: Long = 0

  val starttime: Long = System.currentTimeMillis
  val worker = context.actorOf(Props[Worker].withRouter(RoundRobinRouter(numberofactors)), name = "worker")
   
  def receive = {

    case Count =>    
     println("Bitcoin mining started...")
     for(i <- 1 to numberofactors)
         {
    	   worker ! WorkerProcess(k,i+4, startime)
         }
    
    case MiningCoins(prefix, shamsg)  =>
      		 bitCoin ! MiningCoins(prefix, shamsg)
	     

    case WorkFinish(finish)  =>
        noworkerfinish+= 1
		cputime+=finish
	     if(noworkerfinish == numberofactors){
	       println("final result")
	       bitCoin ! AllFinish(cputime)
	     }
      	
  }
}

class Worker extends Actor {
  var Counter=0
  private val sha = MessageDigest.getInstance("SHA-256")
  
  def searchCoins(k:Int, j:Int, start: Long)=
   {
     
     while(System.currentTimeMillis()-start <= 30000)
      { 
      var numofzeros=""
      for(b<-1 to k)
       {
        numofzeros=numofzeros+"0"
       }
     
      for(a<-1 to 500000)
       { 
        var prefix="dongsijie"+scala.util.Random.alphanumeric.take(4+j).mkString+" "
        var shamsg= hex_digest(prefix)
        Counter=Counter+1
        if(shamsg.startsWith(numofzeros)==true)
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
    
    case WorkerProcess(k, j, start) =>
      searchCoins(k, j, start)
      sender ! WorkFinish(System.currentTimeMillis()-start)
      context.stop(self)
  }
}

object BitMain extends App{
  override def main(args: Array[String]): Unit ={
   val k: Int = args(0).toInt
   println("The number of 0s is:"+k)
   val numberofactors=4
   val hostIP = InetAddress.getLocalHost.getHostAddress()
   val config = ConfigFactory.parseString("""
	    akka {
			actor {
				provider = "akka.remote.RemoteActorRefProvider"
			}
			
			remote {
				enabled-transports = ["akka.remote.netty.tcp"]
				
				netty.tcp {
					hostname = """ + hostIP + """
					port = 5150
				}
			}
		}
	""")
	
	val bitcoinserver = ActorSystem("bitcoinserver", ConfigFactory.load(config))
	val bitcoin = bitcoinserver.actorOf(Props(new BitCoin(bitcoinserver,k)), "bitcoin")
  }  
}



