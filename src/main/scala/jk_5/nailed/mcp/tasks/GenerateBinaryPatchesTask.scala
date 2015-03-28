package jk_5.nailed.mcp.tasks

import java.io._
import java.util
import java.util.jar.Pack200.Packer
import java.util.jar._
import java.util.zip.{Adler32, ZipEntry}

import com.google.common.base.{CharMatcher, Charsets, Splitter}
import com.google.common.collect.{ArrayListMultimap, Iterables}
import com.google.common.io.{ByteStreams, Files, LineProcessor}
import com.nothome.delta.Delta
import jk_5.nailed.mcp.Constants
import jk_5.nailed.mcp.delayed.{DelayedFile, DelayedFileTree}
import lzma.streams.LzmaOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.{InputFile, OutputFile, TaskAction}

import scala.collection.convert.wrapAsScala._
import scala.collection.mutable

/**
 * No description given
 *
 * @author jk-5
 */
class GenerateBinaryPatchesTask extends DefaultTask {

  @InputFile private var dirtyJar: DelayedFile = _
  @OutputFile private var outJar: DelayedFile = _
  private val patchList = new util.ArrayList[DelayedFileTree]()
  @InputFile private var srg: DelayedFile = _

  private val obfMapping = mutable.HashMap[String, String]()
  private val srgMapping = mutable.HashMap[String, String]()
  private val innerClasses = ArrayListMultimap.create[String, String]()
  private val patchedFiles = mutable.HashSet[String]()
  private val delta = new Delta

  @TaskAction def doTask(){
    loadMappings()
    for(tree <- patchList) for(patch <- tree.call().getFiles){
      val name = patch.getName.replace(".java.patch", "")
      val obfName = srgMapping.get(name).get
      patchedFiles.add(obfName)
      addInnerClasses(name, patchedFiles)
    }

    val patches = mutable.HashMap[String, Array[Byte]]()
    createBinPatches(patches, "server/", getCleanJar, getDirtyJar)

    var patchData = createPatchJar(patches)
    patchData = pack200(patchData)
    patchData = compress(patchData)

    buildOutput(patchData)
  }

  private def addInnerClasses(parent: String, patchList: mutable.HashSet[String]){
    for(inner <- innerClasses.get(parent)){
      patchList += srgMapping.get(inner).orNull
      addInnerClasses(inner, patchList)
    }
  }

  private def loadMappings(){
    Files.readLines(getSrg, Charsets.UTF_8, new LineProcessor[String](){
      val splitter = Splitter.on(CharMatcher.anyOf(": ")).omitEmptyStrings().trimResults()

      override def processLine(line: String): Boolean = {
        if(line.startsWith("CL")){
          val parts = Iterables.toArray(splitter.split(line), classOf[String])
          obfMapping.put(parts(1), parts(2))
          val srgName = parts(2).substring(parts(2).lastIndexOf('/') + 1)
          srgMapping.put(srgName, parts(1))
          val innerDollar = srgName.lastIndexOf('$')
          if(innerDollar > 0){
            innerClasses.put(srgName.substring(0, innerDollar), srgName)
          }
        }
        true
      }
      override def getResult: String = null
    })
  }

  private def createBinPatches(patches: mutable.HashMap[String, Array[Byte]], root: String, base: File, target: File){
    val cleanJ = new JarFile(base)
    val dirtyJ = new JarFile(target)

    for(entry <- obfMapping){
      val obf = entry._1
      val srg = entry._2

      if(patchedFiles.contains(obf)){
        val cleanEntry = cleanJ.getJarEntry(obf + ".class")
        val dirtyEntry = dirtyJ.getJarEntry(obf + ".class")
        if(dirtyEntry != null){
          val clean = if(cleanEntry != null) ByteStreams.toByteArray(cleanJ.getInputStream(cleanEntry)) else new Array[Byte](0)
          val dirty = ByteStreams.toByteArray(dirtyJ.getInputStream(dirtyEntry))
          val diff = delta.compute(clean, dirty)
          val out = ByteStreams.newDataOutput(diff.length + 50)
          out.writeUTF(obf)                    //Clean name
          out.writeUTF(obf.replace('/', '.'))  //Source Notch name
          out.writeUTF(srg.replace('/', '.'))  //Source SRG name
          out.writeBoolean(cleanEntry != null) //Exists in Clean
          if(cleanEntry != null){
            out.writeInt(adlerHash(clean))
          }
          out.writeInt(diff.length)
          out.write(diff)

          patches.put(root + srg.replace('/', '.') + ".binpatch", out.toByteArray)
        }
      }
    }

    cleanJ.close()
    dirtyJ.close()
  }

  private def adlerHash(input: Array[Byte]): Int = {
    val hasher = new Adler32
    hasher.update(input)
    hasher.getValue.toInt
  }

  private def createPatchJar(patches: mutable.HashMap[String, Array[Byte]]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val jar = new JarOutputStream(out)
    for(entry <- patches){
      jar.putNextEntry(new JarEntry("binpatch/" + entry._1))
      jar.write(entry._2)
    }
    jar.close()
    out.toByteArray
  }

  private def pack200(data: Array[Byte]): Array[Byte] = {
    val in = new JarInputStream(new ByteArrayInputStream(data))
    val out = new ByteArrayOutputStream
    val packer = Pack200.newPacker()
    val props = packer.properties()
    props.put(Packer.EFFORT, "9")
    props.put(Packer.KEEP_FILE_ORDER, Packer.TRUE)
    props.put(Packer.UNKNOWN_ATTRIBUTE, Packer.PASS)

    val err = new PrintStream(System.err)
    System.setErr(new PrintStream(Constants.getNullStream))
    packer.pack(in, out)
    System.setErr(err)

    in.close()
    out.close()

    out.toByteArray
  }

  private def compress(data: Array[Byte]): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val lzma = new LzmaOutputStream.Builder(out).useEndMarkerMode(true).build()
    lzma.write(data)
    lzma.close()
    out.toByteArray
  }

  private def buildOutput(patchData: Array[Byte]){
    val out = new JarOutputStream(new FileOutputStream(getOutJar))
    val in = new JarFile(getDirtyJar)
    if(patchData != null){
      out.putNextEntry(new JarEntry("binpatches.pack.lzma"))
      out.write(patchData)
    }

    for(e <- in.entries()){
      if(!e.isDirectory){
        if(e.getName.endsWith(".class") && !obfMapping.contains(e.getName.replace(".class", ""))){
          val n = new ZipEntry(e.getName)
          n.setTime(e.getTime)
          out.putNextEntry(n)
          out.write(ByteStreams.toByteArray(in.getInputStream(e)))
        }
      }
    }
    out.close()
    in.close()
  }

  def setDirtyJar(dirtyJar: DelayedFile) = this.dirtyJar = dirtyJar
  def setOutJar(outJar: DelayedFile) = this.outJar = outJar
  def setSrg(srg: DelayedFile) = this.srg = srg
  def addPatchList(patchList: DelayedFileTree) = this.patchList.add(patchList)

  @InputFile
  def getCleanJar = this.getProject.getConfigurations.getByName(Constants.MCJAR_CONFIGURATION).getSingleFile
  def getDirtyJar = dirtyJar.call()
  def getOutJar = outJar.call()
  def getSrg = srg.call()
}
