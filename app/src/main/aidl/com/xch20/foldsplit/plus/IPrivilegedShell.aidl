package com.xch20.foldsplit.plus;

import android.os.Bundle;

interface IPrivilegedShell {
    // Shizuku 约定的销毁事务号
    void destroy() = 16777114;

    Bundle execute(in String[] command) = 1;

    Bundle executeVivoFreeformConfig(in String[] packageNames) = 2;

    // displayRatio：0 全屏，1 4:3，2 16:9，3 与外屏相同，4 全屏布局优化，-1 删除记录（恢复系统默认）
    Bundle setInnerDisplayRatio(String packageName, int displayRatio) = 3;

    Bundle readSecureSetting(String key) = 4;

    Bundle inspectVivoApis() = 5;
}
