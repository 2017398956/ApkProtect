/**
 * If you run this script not on windows, please download jad from https://varaneckas.com/jad/
 * add buildscript at head of module's build.gradle
 */
import javassist.ClassPool
import javassist.CtField
import javassist.bytecode.ClassFile
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.accessors.runtime.functionToAction
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.CRC32
import java.util.zip.Checksum
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.experimental.and

buildscript {
    dependencies {
        classpath("org.javassist:javassist:3.30.2-GA")
    }
}

// FIXME:change to your jad
val jadPath = "${project.projectDir.absolutePath}${File.separator}jad.exe"

project.afterEvaluate {
    val processResTasks = project.tasks.filter { tempTask ->
        var filter = false
        android.applicationVariants.forEach { variant ->
            if (tempTask.name == "process${variant.name.capitalized()}Resources") {
                logger.error("task:${tempTask.name}")
                filter = true
                return@forEach
            }
        }
        filter
    }
    logger.error("processResTasks.size:${processResTasks.size}")
    processResTasks.forEach { task ->
        task.actions.add(1, functionToAction {
//            throw Exception("===============================================")
        })

        task.doLast {
            val newPkgId = 0x6D
            val newPkgIdStr = "0x" + Integer.toHexString(newPkgId)
            task.outputs.files.forEach {
                logger.error("output file path:${it.absolutePath}")
                if (it.name.endsWith("R.jar")) {
                    logger.error("      time:${it.lastModified()}")
                    dealJarFile(it, newPkgIdStr)
                } else if (it.name.endsWith("stableIds.txt")) {
                    logger.error("      time:${it.lastModified()}")
                    replaceResIdInRText(it, newPkgIdStr)
                } else if (it.name.endsWith("R.txt")) {
                    logger.error("      time:${it.lastModified()}")
                    replaceResIdInRText(it, newPkgIdStr)
                } else if (it.name.endsWith("out")) {
                    it.listFiles()?.forEach { ap_ ->
                        if (ap_.name.endsWith(".ap_")) {
                            logger.error("      time:${ap_.lastModified()}")
                            dealApFile(ap_, newPkgId, android.defaultConfig.applicationId!!)
                        }
                    }
                } else {
                    logger.error("      time:${it.lastModified()}")
                }
            }
            logger.error(("exec task:${task.name}"))
//            throw Exception("===============================================")
        }
    }

}

fun replaceResIdInRText(textSymbolOutputFile: File, newPkgIdStr: String) {
    val lines = textSymbolOutputFile.readLines(Charsets.UTF_8).map {
        it.replace("0x7f", newPkgIdStr, true)
    }
    textSymbolOutputFile.delete()
    textSymbolOutputFile.createNewFile()
    lines.forEach {
        textSymbolOutputFile.appendText(it, Charsets.UTF_8)
        textSymbolOutputFile.appendText("\n", Charsets.UTF_8)
    }
}

fun dealJarFile(jarFile: File, newPkgIdStr: String) {
    val outDirPath = jarFile.parentFile.absolutePath + File.separator + "R"
    unZip(jarFile, outDirPath)
    jarFile.delete()
    val newClassPath = replaceResIdInJar(file(outDirPath), newPkgIdStr, outDirPath)
    zipFolder(newClassPath, jarFile.absolutePath, object : ZipStrategy(outDirPath) {
        override fun compressed(): Boolean {
            return false
        }
    }, true)
    file(outDirPath).deleteRecursively()
    file(newClassPath).deleteRecursively()
}

fun dealJarFile2(jarFile: File, newPkgId: Int) {
    val outDirPath = jarFile.parentFile.absolutePath + File.separator + "R"
    unZip(jarFile, outDirPath)
    jarFile.delete()
    val newClassPath = replaceResIdInJar2(File(outDirPath), newPkgId shl 24, outDirPath)
    file(outDirPath).deleteRecursively()
    zipFolder(newClassPath, jarFile.absolutePath, object : ZipStrategy(outDirPath) {
        override fun compressed(): Boolean {
            return false
        }
    }, true)
    File(newClassPath).deleteRecursively()
}

fun executeCommand(cmd: String): Boolean {
    return executeCommand(cmd, false)
}

fun executeCommand(cmd: String, printLog:Boolean): Boolean {
    if (printLog) {
        println("开始执行命令===>$cmd")
    }
    val process: Process = Runtime.getRuntime().exec("cmd /c $cmd")
    consumeInputStream(process.inputStream)
    consumeInputStream(process.errorStream)
    process.waitFor()
    if (process.exitValue() != 0) {
        throw RuntimeException("执行命令错误===>$cmd")
    }
    return true
}

fun consumeInputStream(`is`: InputStream?) {
    val br: BufferedReader = BufferedReader(InputStreamReader(`is`))
    var s: String?
    //        StringBuilder sb = new StringBuilder();
    while ((br.readLine().also { s = it }) != null) {
        println(s)
        //            sb.append(s);
    }
}

fun replaceResIdInJar(classFile: File, newPkgIdStr: String, rootDirPath: String): String {
    if (classFile.isFile()) {
        if (!classFile.name.endsWith("R.class")) {
            return rootDirPath
        }
        val javaSourcePath = "${classFile.parent}${File.separator}R.java"
        val rFile = file(javaSourcePath)
        executeCommand("$jadPath -p ${classFile.absolutePath} > $javaSourcePath")
        val fileReader = FileReader(rFile)
        val lines = ArrayList<String>()
        fileReader.readLines().forEach {
            lines.add(it.replace("0x7f", newPkgIdStr))
        }
        fileReader.close()
        val fileWriter = FileWriter(rFile)
        lines.forEach {
            fileWriter.write(it)
            fileWriter.write("\n")
        }
        fileWriter.close()
        executeCommand("javac $javaSourcePath")
        rFile.delete()
    } else {
        classFile.listFiles()?.forEach {
            replaceResIdInJar(it, newPkgIdStr, rootDirPath)
        }
    }
    return rootDirPath
}

fun replaceResIdInJar2(classFile: File, newPkgId: Int, rootDirPath: String): String {
    val newClassPath = File(rootDirPath).parentFile.absolutePath + File.separator + "RTemp"
//    logger.error("classFileName:${classFile.absolutePath.substring(rootDirPath.length + 1)}")
    if (classFile.isFile) {
        if (!classFile.name.endsWith(".class")
//            || classFile.absolutePath.substring(rootDirPath.length + 1).startsWith("androidx")
        ) {
            return newClassPath
        }
        val fileInputStream = FileInputStream(classFile)
        val dataInputStream = DataInputStream(fileInputStream)
        val clazzFile = ClassFile(dataInputStream)
        fileInputStream.close()
        dataInputStream.close()
        val ctClass = ClassPool.getDefault().makeClass(clazzFile)
        ctClass.defrost()
        val oldFields = ctClass.fields
        oldFields.forEach {
            if (it.type.name == "int") {
                val value = it.constantValue as Int
                if (value and 0x7F000000 == 0x7F000000) {
                    ctClass.removeField(it)
                    val field = CtField(it, ctClass)
                    ctClass.addField(
                        field,
                        CtField.Initializer.constant(value and 0x00FFFFFF or newPkgId)
                    )
                }
            }
        }
        ctClass.writeFile(newClassPath)
        ctClass.defrost()
    } else {
        classFile.listFiles()?.forEach {
            replaceResIdInJar2(it, newPkgId, rootDirPath)
        }
    }
    return newClassPath
}

fun dealApFile(packageOutputFile: File, newPkgId: Int, pkgName: String) {
    logger.warn("dealApFile new package name:${pkgName}")
    val prefixIndex = packageOutputFile.path.lastIndexOf(".")
    val unzipPath = packageOutputFile.path.substring(0, prefixIndex) + File.separator
    unZip(packageOutputFile, unzipPath)
    replaceResIdInResDir(unzipPath, newPkgId)
    replaceResIdInArsc(File(unzipPath, "resources.arsc"), newPkgId, pkgName)
    zipFolder(unzipPath, packageOutputFile.path, object : ZipStrategy(unzipPath) {
        override fun compressed(): Boolean {
            return filePath.endsWith(".xml")
        }
    }, true)
    file(unzipPath).deleteRecursively() //如果需要可以在处理后删除解压后的文件
}

fun replaceResIdInResDir(resPath: String, newPkgId: Int) {
    val resFile = file(resPath)
    if (resFile.isFile()) {
        if (resPath.endsWith(".xml")) {
            replaceResIdInXml(resFile, newPkgId)
        }
    } else {
        val fileList = resFile.list()
        if (fileList == null || fileList.isEmpty()) {
            return
        }
        for (i in 0 until fileList.size) {
            replaceResIdInResDir(resPath + File.separator + fileList.get(i), newPkgId)
        }
    }
}

fun replaceResIdInXml(resFile: File, newPkgId: Int) {
    val buf = resFile.readBytes()
    if (buf[0].toInt() == 0x03 && buf[1].toInt() == 0x00) {
        val xmlHeaderSize = convert2ByteToInt(buf, 2)
        val stringsPoolStart = xmlHeaderSize
        if (buf[stringsPoolStart].toInt() == 0x01 && buf[stringsPoolStart + 1].toInt() == 0x00) {
            val stringsPoolSize = convert4ByteToInt(buf, stringsPoolStart + 4)
            val idsPoolStart = stringsPoolStart + stringsPoolSize
            if (buf[idsPoolStart].toInt()
                    .and(0xff) == 0x80 && buf[idsPoolStart + 1].toInt() == 0x01
            ) {
                val idsPoolHeaderSize = convert2ByteToInt(buf, idsPoolStart + 2)
                val idsPoolSize = convert4ByteToInt(buf, idsPoolStart + 4)
                val idCount = (idsPoolSize - idsPoolHeaderSize) / 4
                val idsPoolDataStart = idsPoolStart + idsPoolHeaderSize
                for (idIndex in 0 until idCount) {
                    if (buf[idsPoolDataStart + idIndex * 4 + 3].toInt() == 0x7f) {
                        buf[idsPoolDataStart + idIndex * 4 + 3] = newPkgId.toByte()
                    }
                }
                var elementStart = idsPoolStart + idsPoolSize
                while (elementStart < buf.size) {
                    if (buf[elementStart].toInt() == 0x04 && buf[elementStart + 1].toInt() == 0x01) {
                        val dataStart = elementStart + convert2ByteToInt(buf, elementStart + 2)
                        val valueStart = dataStart + 4
                        if ((buf[valueStart + 3].toInt() == 0x01 || buf[valueStart + 3].toInt() == 0x02) &&
                            buf[valueStart + 7].toInt() == 0x7f
                        ) {
                            buf[valueStart + 7] = newPkgId.toByte()
                        }
                    } else if (buf[elementStart].toInt() == 0x02 && buf[elementStart + 1].toInt() == 0x01) {
                        val dataStart = elementStart + convert2ByteToInt(buf, elementStart + 2)
                        val attributeStart = convert2ByteToInt(buf, dataStart + 8)
                        val attributeSize = convert2ByteToInt(buf, dataStart + 10)
                        val attributeCount = convert2ByteToInt(buf, dataStart + 12)
                        val attrDataListStart = dataStart + attributeStart
                        var valueStart = attrDataListStart + 12
                        for (attrIndex in 0 until attributeCount) {
                            if ((buf[valueStart + 3].toInt() == 0x01 || buf[valueStart + 3].toInt() == 0x02) &&
                                buf[valueStart + 7].toInt() == 0x7f
                            ) {
                                buf[valueStart + 7] = newPkgId.toByte()
                            }
                            valueStart += attributeSize
                        }
                    }
                    elementStart += convert4ByteToInt(buf, elementStart + 4)
                }
            } else {
                logger.error("parse ${resFile.absolutePath}'s ids pool failed!")
            }
        } else {
            logger.error("parse ${resFile.absolutePath}'s strings pool failed!")
        }
    } else {
        logger.error("parse xml ${resFile.absolutePath} failed!")
    }

    val outStream = FileOutputStream(resFile)
    outStream.write(buf, 0, buf.size)
    outStream.flush()
    outStream.close()
}

fun convert4ByteToInt(buf: ByteArray, startIndex: Int): Int {
//    //
//    logger.error(
//        "before convert:${buf[startIndex + 3].toString(16)},${
//            buf[startIndex + 2].toString(
//                16
//            )
//        },${buf[startIndex + 1].toString(16)},${(buf[startIndex].toInt() and 0xff).toString(16)}"
//    )
//    //
//    logger.error("${buf[startIndex].toUInt().toString(16)}")

    return buf[startIndex + 3].toInt().and(0xff).shl(24) or
            buf[startIndex + 2].toInt().and(0xff).shl(16) or
            buf[startIndex + 1].toInt().and(0xff).shl(8) or
            buf[startIndex].toInt().and(0xff);
}

fun convert2ByteToInt(buf: ByteArray, startIndex: Int): Int {
    return buf[startIndex + 1].toInt().and(0xff).shl(8) or
            buf[startIndex].toInt().and(0xff);
}

fun replaceResIdInArsc(resFile: File, newPkgId: Int, pkgName: String) {
    val buf = resFile.readBytes()
    val dynamicRefBytes = getDynamicRef(pkgName, newPkgId)
    //
    val size = buf.size + dynamicRefBytes.size
    logger.warn("old arsc file size:${buf.size} new arsc file size:$size")
    buf[4] = (size and 0x000000ff).toByte()
    buf[5] = (size and 0x0000ff00).shr(8).toByte()
    buf[6] = (size and 0x00ff0000).shr(16).toByte()
    buf[7] = (size and 0xff000000.toInt()).shr(24).toByte()
    logger.warn("rewrite arsc file size:${convert4ByteToInt(buf, 4)}")
    //
    val DEFAULT_PKG_ID = 0x7F
    //
    if (buf[0].toInt() == 0x02 && buf[1].toInt() == 0) {
        val tabHeaderSize = convert2ByteToInt(buf, 2)
        val packageCount = convert4ByteToInt(buf, 8)
        logger.warn("tabHeaderSize:${tabHeaderSize}")
        if (buf[tabHeaderSize].toInt() == 0x01 && buf[tabHeaderSize + 1].toInt() == 0) {
            val globalStringsPoolSize = convert4ByteToInt(buf, tabHeaderSize + 4)
            logger.warn("globalStringsPoolSize:$globalStringsPoolSize")
            var packageStart = tabHeaderSize + globalStringsPoolSize
            logger.warn("packageStart:$packageStart")
            for (packageIndex in 0 until packageCount) {
                val packageSize = convert4ByteToInt(buf, packageStart + 4)
                val maxSize = tabHeaderSize + globalStringsPoolSize + packageSize
                logger.warn("max size:${maxSize}")
                if (buf[packageStart].toInt() == 0x00 && buf[packageStart + 1].toInt() == 0x02) {
                    if (buf[packageStart + 8].toInt() == DEFAULT_PKG_ID) {
                        buf[packageStart + 8] = newPkgId.toByte()
                        val newPackageSize = packageSize + dynamicRefBytes.size
                        buf[packageStart + 4] = (newPackageSize and 0x000000ff).toByte()
                        buf[packageStart + 5] = (newPackageSize and 0x0000ff00).shr(8).toByte()
                        buf[packageStart + 6] = (newPackageSize and 0x00ff0000).shr(16).toByte()
                        buf[packageStart + 7] =
                            (newPackageSize and 0xff000000.toInt()).shr(24).toByte()
                    } else {
                        logger.error("can not find default package id.")
                        return
                    }
                    val resTypeStringPoolStart =
                        packageStart + convert2ByteToInt(buf, packageStart + 2)
                    val resTypeStringPoolSize = convert4ByteToInt(buf, resTypeStringPoolStart + 4)
                    val resNameStringPoolStart = resTypeStringPoolStart + resTypeStringPoolSize
                    val resNameStringPoolSize = convert4ByteToInt(buf, resNameStringPoolStart + 4)
                    var typeSpecStart = resNameStringPoolStart + resNameStringPoolSize
                    while (true) {
                        logger.warn("read type spec at $typeSpecStart")
                        if (typeSpecStart < maxSize && buf[typeSpecStart].toInt() == 0x02 && buf[typeSpecStart].toInt() == 0x02) {
                            val typeSpecSize = convert4ByteToInt(buf, typeSpecStart + 4)
                            var typeStart = typeSpecStart + typeSpecSize;
                            while (true) {
                                if (typeStart < maxSize && buf[typeStart].toInt() == 0x01 && buf[typeStart + 1].toInt() == 0x02) {
                                    val typeHeaderSize = convert2ByteToInt(buf, typeStart + 2)
                                    val typeSize = convert4ByteToInt(buf, typeStart + 4)
                                    val entryCount = convert4ByteToInt(buf, typeStart + 12)
                                    val entriesStart = convert4ByteToInt(buf, typeStart + 16)
                                    val entriesOffsetsStart = typeStart + typeHeaderSize
                                    for (entryIndex in 0 until entryCount) {
                                        val entryOffset =
                                            convert4ByteToInt(
                                                buf,
                                                entriesOffsetsStart + entryIndex * 4
                                            )
                                        if (entryOffset < 0) {
                                            continue
                                        }
                                        val entryStart = typeStart + entriesStart + entryOffset
                                        var valueStart: Int
                                        if (buf[entryStart + 2].toInt() == 0x01 && buf[entryStart + 3].toInt() == 0) {
                                            // bag
                                            if (buf[entryStart + 11].toInt() == DEFAULT_PKG_ID) {
                                                buf[entryStart + 11] = newPkgId.toByte()
                                            }
                                            val mapCount = convert4ByteToInt(buf, entryStart + 12)
                                            var mapStart: Int
                                            for (mapIndex in 0 until mapCount) {
                                                mapStart = entryStart + 16 + mapIndex * 12
                                                if (buf[mapStart + 3].toInt() == DEFAULT_PKG_ID) {
                                                    buf[mapStart + 3] = newPkgId.toByte()
                                                }
                                                valueStart = mapStart + 4
                                                if ((buf[valueStart + 3].toInt() == 0x01 || buf[valueStart + 3].toInt() == 0x02) &&
                                                    buf[valueStart + 7].toInt() == DEFAULT_PKG_ID
                                                ) {
                                                    buf[valueStart + 7] = newPkgId.toByte()
                                                }
                                            }
                                        } else {
                                            valueStart = entryStart + 8
                                            if ((buf[valueStart + 3].toInt() == 0x01 || buf[valueStart + 3].toInt() == 0x02) &&
                                                buf[valueStart + 7].toInt() == DEFAULT_PKG_ID
                                            ) {
                                                buf[valueStart + 7] = newPkgId.toByte()
                                            }
                                        }
                                    }
                                    //
                                    typeStart += typeSize
                                    typeSpecStart = typeStart
                                } else {
                                    // logger.error("current TYPE_SPEC's TYPE read completed.")
                                    break
                                }
                            }
                        } else {
                            logger.warn("TYPE_SPEC read completed.")
                            break
                        }
                    }
                    // TODO: 多 package 时暂时无效
                    packageStart += convert4ByteToInt(buf, packageStart + 4)
                } else {
                    logger.error("can not find package.")
                }
            }
        } else {
            logger.error("can not find global strings pool.")
        }
    }
    //
    val outStream = FileOutputStream(resFile)
    outStream.write(buf, 0, buf.size)
    outStream.write(dynamicRefBytes)
    outStream.flush()
    outStream.close()
}

fun getDynamicRef(pkgName: String, newPkgId: Int): ByteArray {
    val typeLength = 2
    val headSizeLength = 2
    val totalSizeLength = 4
    val countLength = 4
    //
    val pkgIdLength = 4
    val pkgNameLength = 256
    val pkgNameByte = pkgName.encodeToByteArray()
    val pkgBuf =
        ByteArray(typeLength + headSizeLength + totalSizeLength + countLength + pkgIdLength + pkgNameLength)
    //
    pkgBuf[0] = 0x03
    pkgBuf[1] = 0x02
    //
    pkgBuf[typeLength] = 0x0c
    pkgBuf[typeLength + 1] = 0x00
    //
    pkgBuf[typeLength + headSizeLength] = (pkgBuf.size and 0x000000ff).toByte()
    pkgBuf[typeLength + headSizeLength + 1] = (pkgBuf.size and 0x0000ff00).shr(8).toByte()
    pkgBuf[typeLength + headSizeLength + 2] = (pkgBuf.size and 0x00ff0000).shr(16).toByte()
    pkgBuf[typeLength + headSizeLength + 3] = (pkgBuf.size and 0xff000000.toInt()).shr(24).toByte()
    //
    pkgBuf[typeLength + headSizeLength + totalSizeLength] = 0x01
    pkgBuf[typeLength + headSizeLength + totalSizeLength + 1] = 0x00
    pkgBuf[typeLength + headSizeLength + totalSizeLength + 2] = 0x00
    pkgBuf[typeLength + headSizeLength + totalSizeLength + 3] = 0x00
    //
    pkgBuf[typeLength + headSizeLength + totalSizeLength + countLength] = newPkgId.toByte()
    pkgBuf[typeLength + headSizeLength + totalSizeLength + countLength + 1] = 0x00
    pkgBuf[typeLength + headSizeLength + totalSizeLength + countLength + 2] = 0x00
    pkgBuf[typeLength + headSizeLength + totalSizeLength + countLength + 3] = 0x00
    //
    for (i in 0 until pkgNameByte.size) {
        pkgBuf[typeLength + headSizeLength + totalSizeLength + countLength + pkgIdLength + i * 2] =
            pkgNameByte[i]
        pkgBuf[typeLength + headSizeLength + totalSizeLength + countLength + pkgIdLength + i * 2 + 1] =
            0x00
    }
    return pkgBuf
}

fun unZip(src: File, savePath: String) {
    logger.error("savePath:$savePath")
    var count = -1;
    var newFile: File?
    var inputstream: InputStream?
    var fos: FileOutputStream?
    var bos: BufferedOutputStream?
    val buf = ByteArray(1024 * 8)
    val zipFile = ZipFile(src)
    val entries = zipFile.entries();
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement();
//        logger.error("entry.name:${entry.name}")
        val filename = savePath + File.separator + entry.getName();
        if (!filename.endsWith("/")) {
            newFile = File(filename)
            if (!newFile.parentFile.exists()) {
                newFile.parentFile.mkdirs()
            }
            newFile.createNewFile()
            inputstream = zipFile.getInputStream(entry)
            fos = FileOutputStream(newFile)
            bos = BufferedOutputStream(fos, 2048)
            while ((inputstream!!.read(buf).apply { count = this }) > -1) {
                bos.write(buf, 0, count)
            }
            bos.flush()
            fos.close()
            inputstream.close()
        }
    }
    zipFile.close()
}

fun zipFolder(
    srcPath: String,
    savePath: String,
    zipStrategy: ZipStrategy,
    keepDirStructure: Boolean
) {
    val saveFile = file(savePath)
    saveFile.delete()
    saveFile.createNewFile()
    val outStream = ZipOutputStream(FileOutputStream(saveFile))
    val srcFile = file(srcPath)
    if (keepDirStructure) {
        zipFile(srcFile, "", outStream, zipStrategy, true)
    } else {
        zipFile(srcFile.absolutePath + File.separator, "", outStream)
    }
    outStream.finish()
    outStream.close()
}

fun zipFile(folderPath: String, fileString: String, out: ZipOutputStream) {
    val srcFile = file(folderPath + fileString)
    if (srcFile.isFile) {
        val zipEntry = ZipEntry(fileString)
        zipEntry.method = if (fileString.endsWith(".xml")) {
            ZipEntry.DEFLATED
        } else {
            ZipEntry.STORED
        }
        val inputStream = FileInputStream(srcFile)
        logger.error("fileString:${fileString}, srcFile:${srcFile.absolutePath}")
        out.putNextEntry(zipEntry)
        var len = -1
        val buf = ByteArray(1024 * 8)
        while (inputStream.read(buf).apply { len = this } != -1) {
            out.write(buf, 0, len)
        }
        out.closeEntry()
    } else {
        val fileList = srcFile.list()
        if (fileList == null || fileList.isEmpty()) {
            val zipEntry = ZipEntry(fileString + File.separator)
            out.putNextEntry(zipEntry)
            out.closeEntry()
        } else {
            fileList.forEach {
                zipFile(
                    folderPath,
                    if (fileString.equals("")) {
                        it
                    } else {
                        fileString + File.separator + it
                    },
                    out
                )
            }
        }

    }
}

fun getCRC32Checksum(bytes: ByteArray): Long {
    val crc32: Checksum = CRC32()
    crc32.update(bytes, 0, bytes.size)
    return crc32.getValue()
}

/**
 * 递归压缩方法
 * @param sourceFile 源文件
 * @param out        zip输出流
 * @param name       压缩后的名称
 * @param keepDirStructure  是否保留原来的目录结构,true:保留目录结构;
 * false:所有文件跑到压缩包根目录下(注意：不保留目录结构可能会出现同名文件,会压缩失败)
 * @throws Exception
 */
private fun zipFile(
    sourceFile: File,
    name: String,
    out: ZipOutputStream,
    zipStrategy: ZipStrategy,
    keepDirStructure: Boolean
) {
    zipStrategy.filePath = sourceFile.absolutePath
    if (sourceFile.isFile) {
        // logger.error("zip file:${name}, path:${sourceFile.absolutePath}")
        // 向zip输出流中添加一个zip实体，构造器中name为zip实体的文件的名字
        val zipEntry = ZipEntry(name)
        var sourceBytes: ByteArray? = null
        zipEntry.method = if (zipStrategy.compressed()) {
            ZipEntry.DEFLATED
        } else {
            zipEntry.size = sourceFile.length()
            sourceBytes = sourceFile.readBytes()
            zipEntry.crc = getCRC32Checksum(sourceBytes)
            ZipEntry.STORED
        }
        out.putNextEntry(zipEntry)
        if (sourceBytes == null) {
            // copy文件到zip输出流中
            val buf: ByteArray = ByteArray(8 * 1024)
            var len: Int
            val inputStream: FileInputStream = FileInputStream(sourceFile)
            while ((inputStream.read(buf).also { len = it }) != -1) {
                out.write(buf, 0, len)
            }
            inputStream.close()
        } else {
            out.write(sourceBytes)
        }
        // Complete the entry
        out.closeEntry()

    } else {
        val listFiles = sourceFile.listFiles()
        if (listFiles == null || listFiles.isEmpty()) {
            // 需要保留原来的文件结构时,需要对空文件夹进行处理
            if (keepDirStructure) {
                // 空文件夹的处理
                out.putNextEntry(ZipEntry("$name/"))
                // 没有文件，不需要文件的 copy
                out.closeEntry()
            }
        } else {
            for (file in listFiles) {
                // 判断是否需要保留原来的文件结构
                if (keepDirStructure) {
                    // 注意：file.getName()前面需要带上父文件夹的名字加一斜杠,
                    // 不然最后压缩包中就不能保留原来的文件结构,即：所有文件都跑到压缩包根目录下了
                    zipFile(
                        file, if (name.isEmpty()) {
                            file.name
                        } else {
                            "$name/${file.name}"
                        }, out, zipStrategy, keepDirStructure
                    )
                } else {
                    zipFile(file, file.name, out, zipStrategy, keepDirStructure)
                }
            }
        }
    }
}

abstract class ZipStrategy(var filePath: String) {
    abstract fun compressed(): Boolean
}
