package com.zangetsu101.pass.keymanagement.session

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CachedPassphrase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DirectPassphrase
