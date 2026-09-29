package org.dromara.fanghu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dromara.fanghu.domain.FileAsset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface FileAssetMapper extends BaseMapper<FileAsset> {
    @Update("UPDATE fh_file_asset SET download_count = download_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementDownloadCount(@Param("id") Long id);
}
