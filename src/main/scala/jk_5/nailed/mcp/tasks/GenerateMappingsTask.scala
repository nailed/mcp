package jk_5.nailed.mcp.tasks

import org.gradle.api.tasks.{TaskAction, OutputFile, InputFiles, InputFile}
import jk_5.nailed.mcp.delayed.DelayedFile
import com.google.common.collect.Lists
import CachedTask.Cached
import java.io.File
import java.util
import scala.collection.mutable
import scala.collection.convert.wrapAsScala._
import jk_5.nailed.mcp.Utils
import net.minecraftforge.srg2source.rangeapplier.{MethodData, SrgContainer}
import org.gradle.api.file.FileCollection
import com.google.common.io.Files
import com.google.common.base.Charsets

/**
 * No description given
 *
 * @author jk-5
 */
class GenerateMappingsTask extends CachedTask {

  @InputFile private var inSrg: DelayedFile = _
  @InputFile private var inExc: DelayedFile = _
  @InputFile private var methodCsv: DelayedFile = _
  @InputFile private var fieldCsv: DelayedFile = _

  @InputFiles private val extraSrgs = Lists.newLinkedList[File]()
  @InputFiles private val extraExcs = Lists.newLinkedList[File]()

  @Cached @OutputFile private var notchToSrg: DelayedFile = _
  @Cached @OutputFile private var notchToMcp: DelayedFile = _
  @Cached @OutputFile private var mcpToNotch: DelayedFile = _
  @Cached @OutputFile private var mcpToSrg: DelayedFile = _
  @Cached @OutputFile private var srgExc: DelayedFile = _
  @Cached @OutputFile private var mcpExc: DelayedFile = _

  @TaskAction def doTask(){
    val methods = mutable.HashMap[String, String]()
    val fields = mutable.HashMap[String, String]()

    for(s <- Utils.newCsvReader(getMethodCsv).readAll()) methods.put(s(0), s(1))
    for(s <- Utils.newCsvReader(getFieldCsv).readAll()) fields.put(s(0), s(1))

    val inSrg = new SrgContainer().readSrg(getInSrg)
    val excRemap = readExtraSrgs(getExtraSrgs, inSrg)

    writeSrgs(inSrg, methods, fields)
    writeExcs(excRemap, methods)
  }

  def readExtraSrgs(extras: FileCollection, inSrg: SrgContainer): mutable.HashMap[String, String] = {
    val extraSrg = new SrgContainer().readSrgs(extras)

    val excRemap = mutable.HashMap[String, String]()

    //Srg -> Notch mappings
    val classMap = inSrg.classMap.inverse()
    val methodMap = inSrg.methodMap.inverse()
    for(e <- extraSrg.methodMap.entrySet()){
      val notchName = remapMethodName(e.getValue.name, methodMap)
      val notchSig = remapSignature(e.getValue.sig, classMap)
      inSrg.methodMap.put(new MethodData(notchName, notchSig), e.getValue)
      excRemap.put(e.getKey.name, e.getValue.name)
    }
    excRemap
  }

  def remapMethodName(name: String, methodMap: util.Map[MethodData, MethodData]) = methodMap.keySet().find(_.name == name) match {
    case Some(m) => methodMap.get(m).name
    case None => name //No mapping found
  }

  def remapSignature(sig: String, classMap: util.Map[String, String]): String = {
    val newSig = new mutable.StringBuilder(sig.length)
    newSig.append("(")
    val params = sig.substring(1, sig.lastIndexOf(')')).split(";")
    params.foreach(p => newSig.append(remap(p, classMap)).append(';'))
    newSig.append(')').append(remap(sig.substring(sig.lastIndexOf(')') + 1), classMap))
    newSig.toString()
  }

  @inline def remap(key: String, mappings: util.Map[String, String]) = if(mappings.containsKey(key)) mappings.get(key) else key

  def writeSrgs(inSrg: SrgContainer, methods: mutable.HashMap[String, String], fields: mutable.HashMap[String, String]){
    getNotchToSrg.getParentFile.mkdirs()
    getNotchToMcp.getParentFile.mkdirs()
    getMcpToSrg.getParentFile.mkdirs()
    getMcpToNotch.getParentFile.mkdirs()

    val notchToSrg = Files.newWriter(getNotchToSrg, Charsets.UTF_8)
    val notchToMcp = Files.newWriter(getNotchToMcp, Charsets.UTF_8)
    val mcpToSrg = Files.newWriter(getMcpToSrg, Charsets.UTF_8)
    val mcpToNotch = Files.newWriter(getMcpToNotch, Charsets.UTF_8)

    for(e <- inSrg.packageMap.entrySet()){
      val line = "PK: " + e.getKey + " " + e.getValue
      notchToSrg.write(line)
      notchToSrg.newLine()
      notchToMcp.write(line)
      notchToMcp.newLine()
      mcpToSrg.write(line)
      mcpToSrg.newLine()
      mcpToNotch.write(line)
      mcpToNotch.newLine()
    }
    for(e <- inSrg.classMap.entrySet()){
      notchToSrg.write("CL: " + e.getKey + " " + e.getValue)
      notchToSrg.newLine()
      notchToMcp.write("CL: " + e.getKey + " " + e.getValue)
      notchToMcp.newLine()
      mcpToSrg.write("CL: " + e.getValue + " " + e.getValue)
      mcpToSrg.newLine()
      mcpToNotch.write("CL: " + e.getValue + " " + e.getKey)
      mcpToNotch.newLine()
    }
    for(e <- inSrg.fieldMap.entrySet()){
      notchToSrg.write("FD: " + e.getKey + " " + e.getValue)
      notchToSrg.newLine()

      val temp = e.getValue.substring(e.getValue.lastIndexOf('/') + 1)
      val mcpName = if(fields.contains(temp)) e.getValue.replace(temp, fields.get(temp).get) else e.getValue

      notchToMcp.write("FD: " + e.getKey + " " + mcpName)
      notchToMcp.newLine()

      mcpToSrg.write("FD: " + mcpName + " " + e.getValue)
      mcpToSrg.newLine()

      mcpToNotch.write("FD: " + mcpName + " " + e.getKey)
      mcpToNotch.newLine()
    }
    for(e <- inSrg.methodMap.entrySet()){
      notchToSrg.write("MD: " + e.getKey + " " + e.getValue)
      notchToSrg.newLine()

      val temp = e.getValue.name.substring(e.getValue.name.lastIndexOf('/')+1)
      val mcpName = if(methods.contains(temp)) e.getValue.toString.replace(temp, methods.get(temp).get) else e.getValue

      notchToMcp.write("MD: " + e.getKey + " " + mcpName)
      notchToMcp.newLine()

      mcpToSrg.write("MD: " + mcpName + " " + e.getValue)
      mcpToSrg.newLine()

      mcpToNotch.write("MD: " + mcpName + " " + e.getKey)
      mcpToNotch.newLine()
    }

    notchToSrg.flush()
    notchToSrg.close()

    notchToMcp.flush()
    notchToMcp.close()

    mcpToSrg.flush()
    mcpToSrg.close()

    mcpToNotch.flush()
    mcpToNotch.close()
  }

  def writeExcs(excRemap: mutable.HashMap[String, String], methods: mutable.HashMap[String, String]){
    getSrgExc.getParentFile.mkdirs()
    getMcpExc.getParentFile.mkdirs()

    val srgOut = Files.newWriter(getSrgExc, Charsets.UTF_8)
    val mcpOut = Files.newWriter(getMcpExc, Charsets.UTF_8)
    for(line <- Files.readLines(getInExc, Charsets.UTF_8)){
      srgOut.write(line)
      srgOut.newLine()

      val split = line.split("=")
      val sigIndex = split(0).indexOf('(')
      val dotIndex = split(0).indexOf('.')

      if(sigIndex == -1 || dotIndex == -1){
        mcpOut.write(line)
        mcpOut.newLine()
      }else{
        var name = split(0).substring(dotIndex+1, sigIndex)
        if(methods.contains(name)) name = methods.get(name).get
        mcpOut.write(split(0).substring(0, dotIndex) + name + split(0).substring(sigIndex) + "=" + split(1))
        mcpOut.newLine()
      }
    }
    for(f <- getExtraExcs){
      for(line <- Files.readLines(f, Charsets.UTF_8)){
        mcpOut.write(line)
        mcpOut.newLine()

        val split = line.split("=")
        val sigIndex = split(0).indexOf('(')
        val dotIndex = split(0).indexOf('.')

        if(sigIndex == -1 || dotIndex == -1){
          srgOut.write(line)
          srgOut.newLine()
        }else{
          var name = split(0).substring(dotIndex+1, sigIndex)
          if(excRemap.contains(name)) name = excRemap.get(name).get
          srgOut.write(split(0).substring(0, dotIndex) + name + split(0).substring(sigIndex) + "=" + split(1))
          srgOut.newLine()
        }
      }
    }
    srgOut.flush()
    srgOut.close()

    mcpOut.flush()
    mcpOut.close()
  }

  def setInSrg(inSrg: DelayedFile) = this.inSrg = inSrg
  def setInExc(inExc: DelayedFile) = this.inExc = inExc
  def setMethodCsv(methodCsv: DelayedFile) = this.methodCsv = methodCsv
  def setFieldCsv(fieldCsv: DelayedFile) = this.fieldCsv = fieldCsv
  def setNotchToSrg(notchToSrg: DelayedFile) = this.notchToSrg = notchToSrg
  def setNotchToMcp(notchToMcp: DelayedFile) = this.notchToMcp = notchToMcp
  def setMcpToNotch(mcpToNotch: DelayedFile) = this.mcpToNotch = mcpToNotch
  def setMcpToSrg(mcpToSrg: DelayedFile) = this.mcpToSrg = mcpToSrg
  def setSrgExc(srgExc: DelayedFile) = this.srgExc = srgExc
  def setMcpExc(mcpExc: DelayedFile) = this.mcpExc = mcpExc

  def addExtraSrg(srg: File) = this.extraSrgs.add(srg)
  def addExtraExc(exc: File) = this.extraExcs.add(exc)

  def getInSrg = this.inSrg.call()
  def getInExc = this.inExc.call()
  def getMethodCsv = this.methodCsv.call()
  def getFieldCsv = this.fieldCsv.call()
  def getExtraSrgs = getProject.files(this.extraSrgs)
  def getExtraExcs = getProject.files(this.extraExcs)
  def getNotchToSrg = this.notchToSrg.call()
  def getNotchToMcp = this.notchToMcp.call()
  def getMcpToNotch = this.mcpToNotch.call()
  def getMcpToSrg = this.mcpToSrg.call()
  def getSrgExc = this.srgExc.call()
  def getMcpExc = this.mcpExc.call()
}
