package com.hmdp.dto;

import com.hmdp.entity.Blog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScrollDTO {
    private List<Blog> List;  //返回的分页数据
    private Long MinTime;          //上一次查询的最大值
    private Integer Offset;    //上一次查询最小score的数量

}
