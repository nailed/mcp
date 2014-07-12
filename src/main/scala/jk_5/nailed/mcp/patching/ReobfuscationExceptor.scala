package jk_5.nailed.mcp.patching

import java.io.{BufferedInputStream, File, FileInputStream, FileNotFoundException}
import java.util
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

import com.google.common.base.Charsets
import com.google.common.io.{ByteStreams, Files, LineProcessor}
import de.oceanlabs.mcp.mcinjector.StringUtil
import org.objectweb.asm._

import scala.collection.convert.wrapAsScala._
import scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
class ReobfuscationExceptor {

  var inputJar: File = _
  var deobfJar: File = _
  var methodCsv: File = _
  var fieldCsv: File = _
  var exceptorConfig: File = _

  var classMap = mutable.HashMap[String, String]()
  var accessMap = mutable.HashMap[String, String]()

  def buildSrg(inSrg: File, outSrg: File){
    if(outSrg.isFile){
      outSrg.delete()
    }

    val fixed = Files.readLines(inSrg, Charsets.UTF_8, new SrgLineProcessor(classMap, accessMap))
    Files.write(fixed.getBytes, outSrg)
  }

  def readConfiguration(){
    val csvData = readCsv()
    val oldInfo = readJar(deobfJar)
    val newInfo = readJar(inputJar)

    classMap = createClassMap(newInfo.map, newInfo.interfaces)
    renameAccess(oldInfo.access, csvData)
    accessMap = mergeAccess(newInfo.access, oldInfo.access)
  }

  private def readCsv() = {
    val csvData = mutable.HashMap[String, String]()
    Array(fieldCsv, methodCsv).filter(_ != null).foreach{ f =>
      Files.readLines(f, Charsets.UTF_8, new LineProcessor[AnyRef]{
        override def processLine(line: String): Boolean = {
          val s = line.split(",")
          csvData.put(s(0), s(1))
          true
        }
        override def getResult = null
      })
    }
    csvData
  }

  private def renameAccess(data: util.HashMap[String, AccessInfo], csvData: mutable.HashMap[String, String]){
    for(info <- data.values) for(i <- info.insns){
      val tmp = csvData.get(i.name)
      i.name = if(tmp.isEmpty) i.name else tmp.get
    }
  }

  private def readJar(inJar: File): JarInfo = {
    var is: ZipInputStream = null
    try{
      try{
        is = new ZipInputStream(new BufferedInputStream(new FileInputStream(inJar)))
      }catch{
        case e: FileNotFoundException => throw new FileNotFoundException("Could not open input file. " + e.getMessage)
      }
      val reader = new JarInfo
      var stop = false
      while(!stop){
        val entry = is.getNextEntry
        if(entry == null){
          stop = true
        }else if(!entry.isDirectory && entry.getName.endsWith(".class")){
          new ClassReader(ByteStreams.toByteArray(is)).accept(reader, 0)
        }
      }
      reader
    }finally{
      if(is != null) is.close()
    }
  }

  private def createClassMap(markerMap: mutable.HashMap[String, String], interfaces: mutable.ArrayBuffer[String]): mutable.HashMap[String, String] = {
    val excMap = Files.readLines(exceptorConfig, Charsets.UTF_8, new LineProcessor[mutable.HashMap[String, String]] {
      val tmp = mutable.HashMap[String, String]()
      override def processLine(line: String): Boolean = {
        if(line.contains(".") || !line.contains("=") || line.startsWith("#")) return true
        val s = line.split("=")
        if(!interfaces.contains(s(0))){
          tmp.put(s(0), s(1) + "_")
        }
        true
      }
      override def getResult: mutable.HashMap[String, String] = tmp
    })
    val map = mutable.HashMap[String, String]()
    for(e <- excMap){
      val renamed = markerMap.get(e._2)
      if(renamed.isDefined){
        map.put(e._1, renamed.get)
      }
    }
    map
  }

  private def mergeAccess(oldData: util.HashMap[String, AccessInfo], newData: util.HashMap[String, AccessInfo]): mutable.HashMap[String, String] = {
    var it = oldData.entrySet().iterator()
    while(it.hasNext){
      val e = it.next()
      val n = newData.get(e.getKey)
      if(n != null && e.getValue.targetEquals(n)){
        it.remove()
        newData.remove(e.getKey)
      }
    }
    val matched = mutable.HashMap[String, String]()
    it = oldData.entrySet().iterator()
    while(it.hasNext){
      val old = it.next().getValue
      val it2 = newData.entrySet().iterator()
      var stop = false
      while(it2.hasNext && !stop){
        val e2 = it2.next()
        val _new = e2.getValue
        if(old.targetEquals(_new) && old.owner == _new.owner && old.desc == _new.desc){
          matched.put(old.owner + "/" + old.name, _new.owner + "/" + _new.name)
          it.remove()
          it2.remove()
          stop = true
        }
      }
    }
    matched
  }

  private class SrgLineProcessor(map: mutable.HashMap[String, String], access: mutable.HashMap[String, String]) extends LineProcessor[String] {
    val out = new mutable.StringBuilder()
    val reg = Pattern.compile("L([^;]+);")

    def rename(cls: String): String = map.get(cls) match {
      case Some(r) => r
      case None => cls
    }

    def rsplit(value: String, delimiter: String): Array[String] = {
      val idx = value.lastIndexOf(delimiter)
      Array[String](value.substring(0, idx), value.substring(idx + 1))
    }

    override def processLine(line: String): Boolean = {
      val split = line.split(" ")
      split(0) match {
        case "CL:" => split(2) = rename(split(2))
        case "FD:" =>
          val s = rsplit(split(2), "/")
          split(2) = rename(s(0)) + "/" + s(1)
        case "MD:" =>
          val s = rsplit(split(3), "/")
          split(3) = rename(s(0)) + "/" + s(1)

          if(access.contains(split(3))){
            split(3) = access.get(split(3)).get
          }

          val m = reg.matcher(split(4))
          val b = new StringBuffer
          while(m.find()){
            m.appendReplacement(b, "L" + rename(m.group(1)).replace("$", "\\$") + ";")
          }
          m.appendTail(b)
          split(4) = b.toString
        case _ => //Meh
      }
      out.append(StringUtil.joinString(util.Arrays.asList(split: _*), " ")).append('\n')
      true
    }

    override def getResult: String = out.toString()
  }

  private class JarInfo extends ClassVisitor(Opcodes.ASM4, null) {

    val map = mutable.HashMap[String, String]()
    val access = new util.HashMap[String, AccessInfo]()
    val interfaces = mutable.ArrayBuffer[String]()
    var className: String = _

    override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]){
      this.className = name
      if((access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE){
        this.interfaces += className
      }
    }

    override def visitField(access: Int, name: String, desc: String, signature: String, value: scala.Any): FieldVisitor = {
      if(name == "__OBFID"){
        if(!className.startsWith("net/minecraft/")){
          throw new RuntimeException("Do not use the __OBFID field or add it to one of your own classes. It does not exist on runtime. " + className)
        }
        map.put(String.valueOf(value) + "_", className)
      }
      null
    }

    override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
      if(className.startsWith("net/minecraft/") && name.startsWith("access$")){
        val path = className + "/" + name + desc
        val info = new AccessInfo(className, name, desc)
        info.access = access
        this.access.put(path, info)

        return new MethodVisitor(Opcodes.ASM4){
          override def visitFieldInsn(opcode: Int, owner: String, name: String, desc: String){
            info.add(opcode, owner, name, desc)
          }
          override def visitMethodInsn(opcode: Int, owner: String, name: String, desc: String){
            info.add(opcode, owner, name, desc)
          }
        }
      }
      null
    }
  }

  private case class AccessInfo(var owner: String, var name: String, var desc: String){
    var access: Int = _
    val insns = mutable.ArrayBuffer[Insn]()
    private var cache: String = null

    def add(opcode: Int, owner: String, name: String, desc: String){
      insns += new Insn(opcode, owner, name, desc)
      cache = null
    }

    override def toString = {
      if(cache == null){
        val builder = new mutable.StringBuilder()
        builder.append('[').append(insns(0))
        for(i <- 1 until insns.size){
          builder.append(", ").append(insns(i))
        }
        builder.append(']')
        cache = builder.toString()
      }
      cache
    }

    def targetEquals(info: AccessInfo) = toString == info.toString
  }

  private case class Insn(opcode: Int, owner: String, var name: String, desc: String){
    override def toString = {
      val op = opcode match {
        case Opcodes.GETSTATIC => "GETSTATIC"
        case Opcodes.PUTSTATIC => "PUTSTATIC"
        case Opcodes.GETFIELD => "GETFIELD"
        case Opcodes.PUTFIELD => "PUTFIELD"
        case Opcodes.INVOKEVIRTUAL => "INVOKEVIRTUAL"
        case Opcodes.INVOKESPECIAL => "INVOKESPECIAL"
        case Opcodes.INVOKESTATIC => "INVOKESTATIC"
        case Opcodes.INVOKEINTERFACE => "INVOKEINTERFACE"
        case o => "UNKNOWN_" + o
      }
      op + " " + owner + "/" + name + " " + desc
    }
  }
}
