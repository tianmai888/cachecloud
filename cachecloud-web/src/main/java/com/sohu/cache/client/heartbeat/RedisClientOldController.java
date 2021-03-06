package com.sohu.cache.client.heartbeat;

import com.google.common.collect.Lists;
import com.sohu.cache.client.service.ClientVersionService;
import com.sohu.cache.constant.ClientStatusEnum;
import com.sohu.cache.dao.AppDao;
import com.sohu.cache.dao.InstanceDao;
import com.sohu.cache.entity.AppDesc;
import com.sohu.cache.entity.InstanceInfo;
import com.sohu.cache.util.ConstUtils;
import com.sohu.cache.util.ObjectConvert;
import com.sohu.cache.web.util.IpUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * redis 客户端连接类
 */
@Controller
@RequestMapping(value = "/cache/client")
public class RedisClientOldController {
    private final Logger logger = LoggerFactory.getLogger(RedisClientOldController.class);

    @Resource
    private AppDao appDao;

    @Resource
    private InstanceDao instanceDao;

    @Resource(name = "clientVersionService")
    private ClientVersionService clientVersionService;

    /**
     * 通过appId返回RedisCluster实例信息
     *
     * @param appId
     */
    @RequestMapping(value = "/redis/cluster/old/{appId}.json")
    public void getClusterByAppIdAndKey(HttpServletRequest request, @PathVariable long appId, Model model) {
        if (!handleRedisApp(appId, request, model, ConstUtils.CACHE_TYPE_REDIS_CLUSTER, false)) {
            return;
        }
        getRedisClusterInfo(request, appId, model);
    }

    private void getRedisClusterInfo(HttpServletRequest request, long appId, Model model) {
        String clientVersion = request.getParameter("clientVersion");

        List<InstanceInfo> instanceList = instanceDao.getInstListByAppId(appId);
        if (instanceList == null || instanceList.isEmpty()) {
            model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
            model.addAttribute("message", "ERROR: appId:" + appId + "实例集合为空 ");
            return;
        }
        String shardsInfo = ObjectConvert.assembleInstance(instanceList);
        if (StringUtils.isBlank(shardsInfo)) {
            model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
            model.addAttribute("message", "ERROR: appId:" + appId + "shardsInfo为空 ");
            return;
        }
        int shardNum = shardsInfo.split(" ").length;
        model.addAttribute("appId", appId);
        model.addAttribute("shardNum", shardNum);
        model.addAttribute("shardInfo", shardsInfo);

        addPkey(clientVersion, appId, model);

        //保存版本信息
        try {
            clientVersionService.saveOrUpdateClientVersion(appId, IpUtil.getIpAddr(request), clientVersion);
        } catch (Exception e) {
            logger.error("redisCluster heart error:" + e.getMessage(), e);
        }
    }

    private void addPkey(String clientVersion, long appId, Model model) {
        if (clientVersion.startsWith("1.7")) {
            AppDesc appDesc = appDao.getAppDescById(appId);
            String pkey = appDesc.getPkey();
            if (StringUtils.isNotBlank(pkey)) {
                model.addAttribute("pkey", pkey);
            } else {
                model.addAttribute("pkey", "");
            }
        }
    }

    /**
     * 通过appId返回RedisSentinel实例信息
     *
     * @param appId
     */
    @RequestMapping(value = "/redis/sentinel/old/{appId}.json")
    public void getSentinelAppById(HttpServletRequest request, @PathVariable long appId, Model model) {
        if (!handleRedisApp(appId, request, model, ConstUtils.CACHE_REDIS_SENTINEL, false)) {
            return;
        }
        getRedisSentinelInfo(request, appId, model);
    }

    private void getRedisSentinelInfo(HttpServletRequest request, long appId, Model model) {
        String clientVersion = request.getParameter("clientVersion");

        List<InstanceInfo> instanceList = instanceDao.getInstListByAppId(appId);
        if (instanceList == null || instanceList.isEmpty()) {
            model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
            model.addAttribute("message", "appId: " + appId + " 实例集合为空 ");
            return;
        }
        String masterName = null;
        List<String> sentinelList = new ArrayList<String>();
        for (InstanceInfo instance : instanceList) {
            if (instance.isOffline()) {
                continue;
            }
            if (instance.getType() == ConstUtils.CACHE_REDIS_SENTINEL
                    && masterName == null
                    && StringUtils.isNotBlank(instance.getCmd())) {
                masterName = instance.getCmd();
            }
            if (instance.getType() == ConstUtils.CACHE_REDIS_SENTINEL) {
                sentinelList.add(instance.getIp() + ":" + instance.getPort());
            }
        }
        String sentinels = StringUtils.join(sentinelList, " ");
        model.addAttribute("sentinels", sentinels);
        model.addAttribute("masterName", masterName);
        model.addAttribute("appId", appId);
        model.addAttribute("status", ClientStatusEnum.GOOD.getStatus());

        addPkey(clientVersion, appId, model);

        //保存版本信息
        try {
            clientVersionService.saveOrUpdateClientVersion(appId, IpUtil.getIpAddr(request), clientVersion);
        } catch (Exception e) {
            logger.error("redisSentinel heart error:" + e.getMessage(), e);
        }
    }

    /**
     * 通过appId返回RedisStandalone实例信息
     *
     * @param appId
     */
    @RequestMapping(value = "/redis/standalone/old/{appId}.json")
    public void getStandaloneAppById(HttpServletRequest request, @PathVariable long appId, Model model) {
        if (!handleRedisApp(appId, request, model, ConstUtils.CACHE_REDIS_STANDALONE, false)) {
            return;
        }
        getRedisStandaloneInfo(request, appId, model);
    }


    private void getRedisStandaloneInfo(HttpServletRequest request, long appId, Model model) {
        String clientVersion = request.getParameter("clientVersion");

        List<InstanceInfo> instanceList = instanceDao.getInstListByAppId(appId);
        String standalone = null;
        for (InstanceInfo instanceInfo : instanceList) {
            if (instanceInfo.isOffline()) {
                continue;
            }
            standalone = instanceInfo.getIp() + ":" + instanceInfo.getPort();
        }
        model.addAttribute("standalone", standalone);
        model.addAttribute("status", ClientStatusEnum.GOOD.getStatus());

        addPkey(clientVersion, appId, model);

        //保存版本信息
        try {
            clientVersionService.saveOrUpdateClientVersion(appId, IpUtil.getIpAddr(request), clientVersion);
        } catch (Exception e) {
            logger.error("redisStandalone heart error:" + e.getMessage(), e);
        }
    }

    /**
     * 检查客户端相关参数
     *
     * @param appId         应用id
     * @param request
     * @param model
     * @param type          应用类型
     * @param isCheckAppKey 是否检测appKey
     * @return
     */
    private boolean handleRedisApp(long appId, HttpServletRequest request, Model model, int type,
            boolean isCheckAppKey) {
        AppDesc appDesc = appDao.getAppDescById(appId);

        if (appDesc == null) {
            model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
            model.addAttribute("message", String.format("appId:%s 不存在", appId));
            return false;
        } else if (appDesc.getType() != type) {
            model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
            model.addAttribute("message",
                    String.format("appId:%s 类型不符,期望类型:%s,实际类型%s,请联系管理员!", appId, type, appDesc.getType()));
            return false;
        } else if (isCheckAppKey) {
            String appKey = request.getParameter("appKey");
            if (StringUtils.isBlank(appKey)) {
                model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
                model.addAttribute("message", String.format("appId=%s,appKey参数为空", appId));
                return false;
            }
            if (!appKey.equals(appDesc.getAppKey())) {
                model.addAttribute("status", ClientStatusEnum.ERROR.getStatus());
                model.addAttribute("message", String.format("appId=%s,appKey:%s错误,与服务端不匹配", appId, appKey));
                return false;
            }
        }
        return true;
    }

}
