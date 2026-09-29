package org.dromara.system.api.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.domain.model.LoginBody;
import org.hibernate.validator.constraints.Length;
import jakarta.validation.constraints.Pattern;

/**
 * 用户注册对象
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterBody extends LoginBody {

    /**
     * 用户名
     */
    @NotBlank(message = "{user.username.not.blank}")
    @Length(min = 2, max = 30, message = "{user.username.length.valid}")
    private String username;

    /**
     * 用户密码
     */
    @NotBlank(message = "{user.password.not.blank}")
    @Length(min = 5, max = 30, message = "{user.password.length.valid}")
//    @Pattern(regexp = RegexConstants.PASSWORD, message = "{user.password.format.valid}")
    private String password;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 平台账号主体，仅允许客户或供应商自助注册。
     */
    @NotBlank(message = "请选择账号类型")
    @Pattern(regexp = "customer|supplier", message = "账号类型不正确")
    private String accountType;

}
