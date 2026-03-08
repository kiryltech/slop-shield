package ai.slopshield.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CachedTypeRegistryTest {

    interface SingleParam<T>
    interface MultiParam<T, U, V>
    interface NoParam

    class SimpleImpl : SingleParam<String>
    class MultiImpl : MultiParam<Int, String, Double>

    open class Base : SingleParam<Long>
    class InheritedImpl : Base()

    @Test
    fun `test resolves single generic type argument`() {
        val types = CachedTypeRegistry.get(SimpleImpl::class, SingleParam::class)
        assertEquals(1, types.size)
        assertEquals(String::class, types[0])
    }

    @Test
    fun `test resolves multiple generic type arguments`() {
        val types = CachedTypeRegistry.get(MultiImpl::class, MultiParam::class)
        assertEquals(3, types.size)
        assertEquals(Int::class, types[0])
        assertEquals(String::class, types[1])
        assertEquals(Double::class, types[2])
    }

    @Test
    fun `test resolves generic type through inheritance hierarchy`() {
        val types = CachedTypeRegistry.get(InheritedImpl::class, SingleParam::class)
        assertEquals(1, types.size)
        assertEquals(Long::class, types[0])
    }

    @Test
    fun `test returns empty list for interface without generic parameters`() {
        class NoParamImpl : NoParam

        val types = CachedTypeRegistry.get(NoParamImpl::class, NoParam::class)
        assertEquals(0, types.size)
    }

    @Test
    fun `test throws exception when interface is not implemented`() {
        assertFailsWith<IllegalArgumentException> {
            CachedTypeRegistry.get(SimpleImpl::class, MultiParam::class)
        }
    }

    @Test
    fun `test caching returns same list instance`() {
        val first = CachedTypeRegistry.get(SimpleImpl::class, SingleParam::class)
        val second = CachedTypeRegistry.get(SimpleImpl::class, SingleParam::class)
        assertSame(first, second, "Should return cached instance")
    }
}
