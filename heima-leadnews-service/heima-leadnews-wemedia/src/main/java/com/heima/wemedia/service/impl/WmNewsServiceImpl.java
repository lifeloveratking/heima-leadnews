package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.model.wemedia.pojos.WmUser;

import com.heima.utils.common.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.heima.common.constants.WemediaConstants;
import sun.security.pkcs11.Secmod;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl  extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    /**
     * 查询文章
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findAll(WmNewsPageReqDto dto) {

        //1.检查参数
        if(dto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //分页参数检查
        dto.checkParam();
        //获取当前登录人的信息
        WmUser user = WmThreadLocalUtil.getUser();
        if(user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        //2.分页条件查询
        IPage page = new Page(dto.getPage(),dto.getSize());
        LambdaQueryWrapper<WmNews> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //状态精确查询
        if(dto.getStatus() != null){
            lambdaQueryWrapper.eq(WmNews::getStatus,dto.getStatus());
        }

        //频道精确查询
        if(dto.getChannelId() != null){
            lambdaQueryWrapper.eq(WmNews::getChannelId,dto.getChannelId());
        }

        //时间范围查询
        if(dto.getBeginPubDate()!=null && dto.getEndPubDate()!=null){
            lambdaQueryWrapper.between(WmNews::getPublishTime,dto.getBeginPubDate(),dto.getEndPubDate());
        }

        //关键字模糊查询
        if(StringUtils.isNotBlank(dto.getKeyword())){
            lambdaQueryWrapper.like(WmNews::getTitle,dto.getKeyword());
        }

        //查询当前登录用户的文章
        lambdaQueryWrapper.eq(WmNews::getUserId,user.getId());

        //发布时间倒序查询
        lambdaQueryWrapper.orderByDesc(WmNews::getCreatedTime);

        page = page(page,lambdaQueryWrapper);

        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(dto.getPage(),dto.getSize(),(int)page.getTotal());
        responseResult.setData(page.getRecords());

        return responseResult;
    }
    @Autowired
    private WmNewsTaskService wmNewsTaskService;
    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        if (dto==null||dto.getContent()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);

        }
        //1.保存或者修改文章
        WmNews wmNews=new WmNews();
        //属性拷贝
        BeanUtils.copyProperties(dto,wmNews);
        //封面图片 list--->string
        if (dto.getImages()!=null&&dto.getImages().size()>0){
            String imagestr=StringUtils.join(dto.getImages(),",");
            wmNews.setImages(imagestr);
        }
        //如果现在封面类型为自动-1

        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }
        saveorupdate(wmNews);
        //2.判断是否为草稿
        if (dto.getStatus().equals(WmNews.Status.NORMAL)){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }
        //获取到文章的图片信息
        List<String> materials= getEctractUrl(dto.getContent());
        //3.不是草稿，保存文章内容与素材的关系
        saveRelativeForContent(materials,wmNews.getId());
        //4.不是草稿，保存文章封面内容与素材的关系
        saveRelativeInfoForCover(dto,wmNews,materials);
        //审核文章
        //审核文章
        //        wmNewsAutoScanService.autoScanWmNews(wmNews.getId());
        wmNewsTaskService.addNewsToTask(wmNews.getId(),wmNews.getPublishTime());

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;
    /**
     * 文章的上下架
     * @param dto
     * @return
     */
    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
        //1.检查参数
        if(dto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //2.查询文章
        WmNews wmNews = getById(dto.getId());
        if(wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"文章不存在");
        }

        //3.判断文章是否已发布
        if(!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"当前文章不是发布状态，不能上下架");
        }

        //4.修改文章enable
        if(dto.getEnable() != null && dto.getEnable() > -1 && dto.getEnable() < 2){
            update(Wrappers.<WmNews>lambdaUpdate().set(WmNews::getEnable,dto.getEnable())
                    .eq(WmNews::getId,wmNews.getId()));

            if (wmNews.getArticleId()!=null){
                //发送消息，通知article；
                Map<String,Object> map=new HashMap<>();
                map.put("articleId",wmNews.getArticleId());
                map.put("enable",wmNews.getEnable());
                kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC,JSON.toJSONString(map));
            }
        }
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 第一功能，如果封面是自动，那么设置封面理性的数据
     * 匹配规则
     * 1.如果内容图片大于等于1，小于3，那么 单图 type 1
     * 2.如果内容图片大于3，那么 type 3
     * 3.如果内存没有图片，那无图， type 0
     *
     * 第二功能
     * 保存封面图片与素材的数据
     * @param dto
     * @param wmNews
     * @param materials
     */

    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {

        List<String> images = dto.getImages();

        //如果当前封面类型为自动，则设置封面类型的数据
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            //多图
            if(materials.size() >= 3){
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            }else if(materials.size() >= 1 && materials.size() < 3){
                //单图
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            }else {
                //无图
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }

            //修改文章
            if(images != null && images.size() > 0){
                wmNews.setImages(StringUtils.join(images,","));
            }
            updateById(wmNews);
        }
        if(images != null && images.size() > 0){
            saveRelativeInfo(images,wmNews.getId(),WemediaConstants.WM_COVER_REFERENCE);
        }

    }

    /**
     * 处理文章内容图片与素材的关系
     * @param materials
     * @param id
     */
    private void saveRelativeForContent(List<String> materials, Integer id) {
       saveRelativeInfo(materials,id,WemediaConstants.WM_CONTENT_REFERENCE);
    }
   @Autowired
   private WmMaterialMapper wmMaterialMapper;
   /**
     * 保存素材与文章的关系到数据库；
     * @param materials
     * @param id
     * @param type
     */
    private void saveRelativeInfo(List<String> materials, Integer id, Short type) {
        if (materials!=null&&!materials.isEmpty()){
        //获取图片url
        List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
        //没有查询出来 五张查出三张
        //判断素材是否有效
        if (wmMaterials==null||wmMaterials.size()==0){
            //手动抛出异常 第一个功能，能够提示调用者的素材失效了，第二个功能，进行数据的回滚
            throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
        }
        if (materials.size()!=wmMaterials.size()){
            throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
        }
        List<Integer> collect = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());
        //批量保存
        mapper.saveRelations(collect,id,type);}
    }


    //读取content中的图片，保存文章内容和素材之间的关系
    private List<String> getEctractUrl(String content) {
        ArrayList<String> list = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map:maps
             ) {
            if (map.get("type").equals("image")){
                String imgUrl=(String) map.get("value");
                list.add(imgUrl);
            }

        }

        return list;
    }

    @Autowired
    private WmNewsMaterialMapper mapper;
    /**
     * 保存或修改文章
     * @param wmNews
     */
    private void saveorupdate(WmNews wmNews) {
        //补全属性
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short)1);
        if (wmNews.getId()==null){
            //保存
            save(wmNews);
        }else {
            //删除素材库中的信息
            mapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId,wmNews.getId()));
            updateById(wmNews);
        }
    }
}