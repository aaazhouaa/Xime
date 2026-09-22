# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# 应用 Lua 5.4 Android 兼容性补丁（修复 32 位设备上 fseeko/ftello 不可用的问题）
# 对 liolib.c 中 l_fseek 配置块做条件增强，使 32 位 Android < API 24 能编译
# 使用 CMake 原生方式修补，无需依赖 git/patch
string(ASCII 10 LUA_NL)
set(LUA_LIOLIB_SRC "${CMAKE_SOURCE_DIR}/librime-lua-deps/lua5.4/liolib.c")
if(EXISTS "${LUA_LIOLIB_SRC}")
  file(READ "${LUA_LIOLIB_SRC}" LUA_LIOLIB_CONTENT)
  # 检查补丁是否已应用
  string(FIND "${LUA_LIOLIB_CONTENT}" "ANDROID" LUA_ALREADY_PATCHED)
  if(LUA_ALREADY_PATCHED EQUAL -1)
    string(FIND "${LUA_LIOLIB_CONTENT}" "#if !defined(l_fseek)" LUA_ANCHOR_POS)
    if(LUA_ANCHOR_POS GREATER -1)
      string(SUBSTRING "${LUA_LIOLIB_CONTENT}" ${LUA_ANCHOR_POS} -1 LUA_SUB_CONTENT)
      string(FIND "${LUA_SUB_CONTENT}" "#if defined(LUA_USE_POSIX)" LUA_REL_POS)
      if(LUA_REL_POS GREATER -1)
        math(EXPR LUA_TARGET_POS "${LUA_ANCHOR_POS} + ${LUA_REL_POS}")
        string(SUBSTRING "${LUA_LIOLIB_CONTENT}" ${LUA_TARGET_POS} -1 LUA_TARGET_CONTENT)
        string(FIND "${LUA_TARGET_CONTENT}" "${LUA_NL}" LUA_REL_NL_POS)
        if(LUA_REL_NL_POS GREATER -1)
          math(EXPR LUA_NL_POS "${LUA_TARGET_POS} + ${LUA_REL_NL_POS}")
          string(SUBSTRING "${LUA_LIOLIB_CONTENT}" 0 ${LUA_TARGET_POS} LUA_HEAD)
          string(SUBSTRING "${LUA_LIOLIB_CONTENT}" ${LUA_NL_POS} -1 LUA_TAIL)
          math(EXPR LUA_SUFFIX_START "${LUA_TARGET_POS} + 26")
          math(EXPR LUA_SUFFIX_LEN "${LUA_NL_POS} - ${LUA_SUFFIX_START}")
          string(SUBSTRING "${LUA_LIOLIB_CONTENT}" ${LUA_SUFFIX_START} ${LUA_SUFFIX_LEN} LUA_SUFFIX)
          set(LUA_PATCHED_LINE
            "#if defined(LUA_USE_POSIX) && \\${LUA_NL}   (!defined(ANDROID) || (defined(__LP64__) || ANDROID_PLATFORM >= 24))${LUA_SUFFIX}")
          set(LUA_LIOLIB_CONTENT "${LUA_HEAD}${LUA_PATCHED_LINE}${LUA_TAIL}")
          file(WRITE "${LUA_LIOLIB_SRC}" "${LUA_LIOLIB_CONTENT}")
        endif()
      endif()
    endif()
  endif()
endif()

# 应用 librime 光标编辑补丁（候选不受光标位置限制）
#
# librime 原生行为：光标位于输入串中间时只翻译光标之前的部分
#   Compose(): active_input = input.substr(0, caret_pos)
#   CalculateSegmentation(): caret 之后只保留一个音段（start_pos >= caret_pos 即 break）
# 导致「编辑前段音节」时后段候选一并丢失/受限。补丁改为整串输入始终参与切分与
# 翻译，光标仅决定插入位置（PushInput/PopInput）与 preedit 光标绘制位置（GetPreedit）。
#
# 与 Lua 补丁同款：CMake 原生就地修补（幂等），不依赖 git/patch 二进制。
# 补丁内容同步留档于 patches/librime-caret.patch。
set(RIME_ENGINE_SRC "${CMAKE_SOURCE_DIR}/librime/src/rime/engine.cc")
if(EXISTS "${RIME_ENGINE_SRC}")
  file(READ "${RIME_ENGINE_SRC}" RIME_ENGINE_CONTENT)
  string(FIND "${RIME_ENGINE_CONTENT}" "XIME_CARET_PATCH" RIME_ALREADY_PATCHED)
  if(RIME_ALREADY_PATCHED EQUAL -1)
    string(REPLACE [==[  const string active_input = ctx->input().substr(0, ctx->caret_pos());
  DLOG(INFO) << "active input: " << active_input;
  comp.Reset(active_input);
  if (ctx->caret_pos() < ctx->input().length() &&
      ctx->caret_pos() == comp.GetConfirmedPosition()) {
    // translate one segment past caret pos.
    comp.Reset(ctx->input());
  }]==] [==[  // XIME_CARET_PATCH: 整串输入始终参与切分与翻译，候选不受光标位置限制
  comp.Reset(ctx->input());]==] RIME_ENGINE_CONTENT "${RIME_ENGINE_CONTENT}")
    string(REPLACE [==[    // only one segment is allowed past caret pos, which is the segment
    // immediately after the caret.
    if (start_pos >= context_->caret_pos())
      break;]==] [==[    // XIME_CARET_PATCH: 不再因 caret 位置提前结束分段]==] RIME_ENGINE_CONTENT "${RIME_ENGINE_CONTENT}")
    file(WRITE "${RIME_ENGINE_SRC}" "${RIME_ENGINE_CONTENT}")
  endif()
endif()

# 已集成的插件
set(RIME_PLUGINS librime-octagram librime-predict librime-t9)

# 将插件复制到 plugins/ 目录。
# 顶层插件目录（librime-t9 等）是唯一权威源码，这里在每次 configure 时
# 都全量同步，确保插件编译副本与顶层一致（file(COPY) 保留源文件时间戳，
# 内容未变的文件不会触发重编译）。
foreach(plugin ${RIME_PLUGINS})
  file(COPY "${CMAKE_SOURCE_DIR}/${plugin}/"
       DESTINATION "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}")
endforeach()

# librime-lua 需要特殊命名 lua
file(COPY "${CMAKE_SOURCE_DIR}/librime-lua/"
     DESTINATION "${CMAKE_SOURCE_DIR}/librime/plugins/lua")

# librime-lua thirdparty 依赖（Lua 5.4 源码）
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/lua/thirdparty")
  file(COPY "${CMAKE_SOURCE_DIR}/librime-lua-deps/"
       DESTINATION "${CMAKE_SOURCE_DIR}/librime/plugins/lua/thirdparty")
endif()

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)
target_compile_options(
  rime-static PRIVATE "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=." "-Wno-error=deprecated-declarations")

target_compile_options(
  rime-lua-objs PRIVATE "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=.")
