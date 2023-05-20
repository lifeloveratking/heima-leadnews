package com.heima.tess4j;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;

public class Application {
    //识别图片中的文字
    public static void main(String[] args) throws TesseractException {
        //创建实例
        ITesseract tesseract=new Tesseract();
        //设置字体库实例
        tesseract.setDatapath("D:\\heima");
        //设置语言
        tesseract.setLanguage("chi_sim");
        File file=new File("D:\\heima\\kenan.png");
        String result=tesseract.doOCR(file);
        result = result.replaceAll("\\r|\\n","-").replaceAll(" ","");
        System.out.println("识别的结果"+result);
    }
}
