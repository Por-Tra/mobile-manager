package com.lucas.mobilemanager.domain.rules

import java.time.Duration

/** Budget commun aux apps sélectionnées ; les temps de zones ne s'y ajoutent jamais. */
data class SharedBudget(val packages: Set<String>, val limit: Duration, val used: Duration) {
    init {
        require(!limit.isNegative && !limit.isZero)
        require(!used.isNegative)
    }
}
