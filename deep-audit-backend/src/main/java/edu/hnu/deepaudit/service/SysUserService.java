package edu.hnu.deepaudit.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import edu.hnu.deepaudit.mapper.biz.SysUserMapper;
import edu.hnu.deepaudit.model.SysUser;
import org.springframework.stereotype.Service;

@Service
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {
}
