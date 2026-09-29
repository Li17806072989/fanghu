package org.dromara.fanghu.domain; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data;
@Data @TableName("fh_file_asset") public class FileAsset extends FanghuEntity { private String ownerType; private Long ownerId; private String objectKey; private String sha256; private String visibility; private String scanStatus; private Integer downloadCount; }
