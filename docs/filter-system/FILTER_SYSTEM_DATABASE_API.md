# Calcvault AR Filter System - Database & API Schemas

## 1. COMPLETE DATABASE SCHEMA

### 1.1 SQLite Database Structure

```sql
-- ============================================================================
-- FILTER DEFINITIONS
-- ============================================================================

CREATE TABLE filters (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL,
    subcategory TEXT,
    version INTEGER DEFAULT 1,
    
    -- Metadata
    author TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Storage
    local_path TEXT,
    download_url TEXT,
    remote_path TEXT,
    file_size_bytes INTEGER,
    compressed_size_bytes INTEGER,
    checksum TEXT,
    signature TEXT,
    
    -- Ratings & Engagement
    rating REAL DEFAULT 0.0,
    rating_count INTEGER DEFAULT 0,
    download_count INTEGER DEFAULT 0,
    usage_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    
    -- Status Flags
    is_favorite INTEGER DEFAULT 0,
    is_downloaded INTEGER DEFAULT 0,
    is_installed INTEGER DEFAULT 0,
    is_premium INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 0,
    is_featured INTEGER DEFAULT 0,
    
    -- System Requirements
    min_api_level INTEGER DEFAULT 21,
    min_ram_mb INTEGER DEFAULT 512,
    min_storage_mb INTEGER DEFAULT 100,
    supported_devices TEXT,  -- JSON array
    
    -- Configuration
    manifest_json TEXT,
    metadata_json TEXT,
    config_json TEXT,
    
    -- Analytics
    created_by_user INTEGER DEFAULT 0,
    is_temporary INTEGER DEFAULT 0,
    
    -- Indexes
    INDEX idx_category (category),
    INDEX idx_favorite (is_favorite),
    INDEX idx_downloaded (is_downloaded),
    INDEX idx_rating (rating DESC),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_usage_count (usage_count DESC)
);

-- ============================================================================
-- FILTER CATEGORIES
-- ============================================================================

CREATE TABLE filter_categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    icon_path TEXT,
    description TEXT,
    position INTEGER,
    
    INDEX idx_position (position)
);

-- Insert default categories
INSERT INTO filter_categories (id, name, icon_path, description, position) VALUES
('beauty', 'Beauty', 'ic_beauty', 'Skin enhancement and beauty effects', 1),
('accessory', 'Accessories', 'ic_accessory', 'Glasses, hats, crowns', 2),
('animal', 'Animals', 'ic_animal', 'Dog, cat, lion filters', 3),
('transform', 'Transform', 'ic_transform', 'Face shape modifications', 4),
('fun', 'Fun', 'ic_fun', 'Funny and playful effects', 5),
('anime', 'Anime', 'ic_anime', 'Anime and manga styles', 6),
('background', 'Background', 'ic_background', 'Background effects', 7),
('professional', 'Professional', 'ic_professional', 'Video call enhancements', 8);

-- ============================================================================
-- FILTER CUSTOMIZATION & PRESETS
-- ============================================================================

CREATE TABLE filter_customizations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    filter_id TEXT NOT NULL,
    preset_name TEXT,
    
    -- Customization data
    customization_json TEXT NOT NULL,  -- JSON of user settings
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_default INTEGER DEFAULT 0,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(user_id, filter_id, preset_name),
    INDEX idx_user_id (user_id),
    INDEX idx_filter_id (filter_id),
    INDEX idx_default (is_default)
);

-- ============================================================================
-- USAGE STATISTICS & ANALYTICS
-- ============================================================================

CREATE TABLE filter_usage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    
    -- Context
    context TEXT NOT NULL,  -- 'camera', 'call', 'gallery_edit'
    used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Performance metrics
    duration_ms INTEGER,
    fps_avg REAL,
    cpu_usage_percent REAL,
    memory_usage_mb REAL,
    battery_drain_percent REAL,
    
    -- Device info
    device_model TEXT,
    android_version INTEGER,
    
    -- Outcome
    was_shared INTEGER DEFAULT 0,
    was_saved INTEGER DEFAULT 0,
    result_saved_path TEXT,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    INDEX idx_filter_id (filter_id),
    INDEX idx_user_id (user_id),
    INDEX idx_context (context),
    INDEX idx_used_at (used_at DESC)
);

-- ============================================================================
-- DOWNLOADED ASSETS CACHE
-- ============================================================================

CREATE TABLE downloaded_assets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    asset_name TEXT NOT NULL,
    asset_type TEXT,  -- '3d_model', '2d_overlay', 'shader', 'animation'
    
    -- File info
    file_path TEXT NOT NULL,
    file_size_bytes INTEGER,
    checksum TEXT,
    compression_type TEXT,  -- 'none', 'gzip', 'zip'
    
    -- Status
    downloaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP,
    expires_at TIMESTAMP,
    is_verified INTEGER DEFAULT 1,
    
    -- Memory
    memory_cached INTEGER DEFAULT 0,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(filter_id, asset_name),
    INDEX idx_filter_id (filter_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_asset_type (asset_type)
);

-- ============================================================================
-- FACE LANDMARK CACHE (for optimization)
-- ============================================================================

CREATE TABLE landmark_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    timestamp LONG NOT NULL,
    
    -- Serialized face data
    face_data BLOB NOT NULL,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_valid INTEGER DEFAULT 1,
    
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at)
);

-- ============================================================================
-- FAVORITES & HISTORY
-- ============================================================================

CREATE TABLE favorite_filters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    filter_id TEXT NOT NULL,
    
    -- Metadata
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    position INTEGER,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(user_id, filter_id),
    INDEX idx_user_id (user_id),
    INDEX idx_position (position)
);

CREATE TABLE recent_filters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    filter_id TEXT NOT NULL,
    
    -- Metadata
    last_used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(user_id, filter_id),
    INDEX idx_user_id (user_id),
    INDEX idx_last_used_at (last_used_at DESC)
);

-- ============================================================================
-- FILTER REVIEWS & RATINGS
-- ============================================================================

CREATE TABLE filter_reviews (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    
    -- Rating
    rating INTEGER NOT NULL CHECK(rating >= 1 AND rating <= 5),
    review_text TEXT,
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    helpful_count INTEGER DEFAULT 0,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(filter_id, user_id),
    INDEX idx_filter_id (filter_id),
    INDEX idx_rating (rating)
);

-- ============================================================================
-- FILTER PACKAGES (for bulk operations)
-- ============================================================================

CREATE TABLE filter_packages (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    version INTEGER DEFAULT 1,
    
    -- Filters in package
    filter_ids TEXT NOT NULL,  -- JSON array
    
    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    size_bytes INTEGER,
    
    -- Status
    is_downloaded INTEGER DEFAULT 0,
    download_url TEXT
);

-- ============================================================================
-- FILTER EFFECTS DEFINITION
-- ============================================================================

CREATE TABLE filter_effects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    effect_name TEXT NOT NULL,
    effect_type TEXT NOT NULL,  -- 'beauty', 'accessory', 'particle', etc.
    
    -- Effect configuration
    config_json TEXT NOT NULL,
    parameters_json TEXT,
    
    -- Rendering
    shader_name TEXT,
    asset_path TEXT,
    
    -- Metadata
    position INTEGER,
    enabled INTEGER DEFAULT 1,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    INDEX idx_filter_id (filter_id),
    INDEX idx_effect_type (effect_type)
);

-- ============================================================================
-- FILTER REACTIONS (for trigger-based effects)
-- ============================================================================

CREATE TABLE filter_reactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    trigger_type TEXT NOT NULL,  -- 'smile', 'blink', 'mouth_open', etc.
    
    -- Reaction effect
    effect_json TEXT NOT NULL,
    duration_ms INTEGER,
    
    -- Metadata
    enabled INTEGER DEFAULT 1,
    priority INTEGER DEFAULT 0,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    INDEX idx_filter_id (filter_id),
    INDEX idx_trigger_type (trigger_type)
);

-- ============================================================================
-- DEVICE COMPATIBILITY
-- ============================================================================

CREATE TABLE device_compatibility (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    device_model TEXT NOT NULL,
    android_version INTEGER NOT NULL,
    
    -- Compatibility info
    is_compatible INTEGER DEFAULT 1,
    performance_level TEXT,  -- 'low', 'medium', 'high'
    recommended_quality TEXT,
    
    -- Testing info
    tested_date TIMESTAMP,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(filter_id, device_model, android_version),
    INDEX idx_device_model (device_model)
);

-- ============================================================================
-- FILTER SYNC & VERSIONING
-- ============================================================================

CREATE TABLE filter_sync_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    
    -- Sync info
    local_version INTEGER,
    remote_version INTEGER,
    sync_status TEXT,  -- 'pending', 'syncing', 'synced', 'failed'
    last_sync_at TIMESTAMP,
    
    -- Error tracking
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    
    -- Relationships
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE CASCADE,
    UNIQUE(filter_id),
    INDEX idx_sync_status (sync_status)
);

-- ============================================================================
-- CREATE INDEXES
-- ============================================================================

CREATE INDEX idx_filters_category_rating ON filters(category, rating DESC);
CREATE INDEX idx_filters_category_updated ON filters(category, updated_at DESC);
CREATE INDEX idx_filter_usage_user_context ON filter_usage(user_id, context);
CREATE INDEX idx_filter_usage_date_range ON filter_usage(used_at DESC);

-- ============================================================================
-- VIEWS FOR COMMON QUERIES
-- ============================================================================

CREATE VIEW popular_filters AS
SELECT id, name, category, rating, download_count, usage_count
FROM filters
WHERE is_downloaded = 1
ORDER BY rating DESC, download_count DESC
LIMIT 20;

CREATE VIEW trending_filters AS
SELECT id, name, category, usage_count
FROM filters
WHERE updated_at > datetime('now', '-7 days')
AND usage_count > 0
ORDER BY usage_count DESC
LIMIT 20;

CREATE VIEW user_favorite_filters AS
SELECT f.id, f.name, f.category, f.rating, ff.added_at
FROM filters f
JOIN favorite_filters ff ON f.id = ff.filter_id
ORDER BY ff.position;
```

---

## 2. REST API SPECIFICATIONS

### 2.1 Filter Store API

```kotlin
// FilterStoreService.kt
interface FilterStoreService {
    
    // ========== FEATURED & DISCOVERY ==========
    
    @GET("v1/filters/featured")
    suspend fun getFeaturedFilters(
        @Query("limit") limit: Int = 20,
        @Query("platform") platform: String = "android"
    ): Response<List<FilterMetadata>>
    
    @GET("v1/filters/trending")
    suspend fun getTrendingFilters(
        @Query("timerange") timeRange: String = "week",  // day, week, month
        @Query("limit") limit: Int = 20
    ): Response<List<FilterMetadata>>
    
    @GET("v1/categories")
    suspend fun getCategories(): Response<List<FilterCategory>>
    
    // ========== SEARCH & BROWSE ==========
    
    @GET("v1/filters/search")
    suspend fun searchFilters(
        @Query("q") query: String,
        @Query("category") category: String? = null,
        @Query("sort_by") sortBy: String = "relevance",  // relevance, rating, trending
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<PaginatedResponse<FilterMetadata>>
    
    @GET("v1/filters/category/{category}")
    suspend fun getFiltersByCategory(
        @Path("category") category: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "rating"
    ): Response<PaginatedResponse<FilterMetadata>>
    
    // ========== FILTER DETAILS ==========
    
    @GET("v1/filters/{id}")
    suspend fun getFilterDetails(
        @Path("id") filterId: String
    ): Response<FilterDetailResponse>
    
    @GET("v1/filters/{id}/preview")
    suspend fun getFilterPreview(
        @Path("id") filterId: String
    ): Response<FilterPreviewResponse>
    
    @GET("v1/filters/{id}/assets")
    suspend fun getFilterAssets(
        @Path("id") filterId: String
    ): Response<FilterAssetsResponse>
    
    // ========== DOWNLOAD ==========
    
    @GET("v1/filters/{id}/download")
    suspend fun downloadFilter(
        @Path("id") filterId: String,
        @Query("version") version: Int? = null
    ): ResponseBody
    
    @POST("v1/filters/{id}/download-start")
    suspend fun notifyDownloadStart(
        @Path("id") filterId: String,
        @Body request: DownloadStartRequest
    ): Response<DownloadStartResponse>
    
    @POST("v1/filters/{id}/download-complete")
    suspend fun notifyDownloadComplete(
        @Path("id") filterId: String,
        @Body request: DownloadCompleteRequest
    ): Response<Unit>
    
    // ========== REVIEWS & RATINGS ==========
    
    @GET("v1/filters/{id}/reviews")
    suspend fun getFilterReviews(
        @Path("id") filterId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("sort") sort: String = "helpful"
    ): Response<PaginatedResponse<FilterReview>>
    
    @POST("v1/filters/{id}/rate")
    suspend fun rateFilter(
        @Path("id") filterId: String,
        @Body rating: FilterRatingRequest
    ): Response<FilterRatingResponse>
    
    @POST("v1/filters/{id}/review")
    suspend fun submitReview(
        @Path("id") filterId: String,
        @Body review: FilterReviewRequest
    ): Response<FilterReview>
    
    // ========== RECOMMENDATIONS ==========
    
    @GET("v1/filters/recommended")
    suspend fun getRecommendedFilters(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 10
    ): Response<List<FilterMetadata>>
    
    // ========== COLLECTIONS ==========
    
    @GET("v1/collections")
    suspend fun getCollections(): Response<List<FilterCollection>>
    
    @GET("v1/collections/{id}")
    suspend fun getCollection(
        @Path("id") collectionId: String
    ): Response<FilterCollectionDetail>
    
    // ========== ANALYTICS ==========
    
    @POST("v1/analytics/filter-usage")
    suspend fun reportFilterUsage(
        @Body usage: FilterUsageReport
    ): Response<Unit>
    
    @POST("v1/analytics/filter-session")
    suspend fun reportFilterSession(
        @Path("id") filterId: String,
        @Body session: FilterSessionReport
    ): Response<Unit>
}

// Request/Response Models

data class FilterMetadata(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val icon_url: String,
    val preview_url: String,
    val rating: Float,
    val rating_count: Int,
    val download_count: Int,
    val version: Int,
    val is_favorite: Boolean = false,
    val is_downloaded: Boolean = false
)

data class FilterDetailResponse(
    val id: String,
    val name: String,
    val category: String,
    val subcategory: String,
    val description: String,
    val long_description: String,
    val icon_url: String,
    val preview_urls: List<String>,
    val author: String,
    val version: Int,
    val file_size: Long,
    val download_url: String,
    val rating: Float,
    val rating_count: Int,
    val download_count: Int,
    val usage_count: Int,
    val is_premium: Boolean,
    val is_featured: Boolean,
    val requirements: FilterRequirements,
    val effects: List<EffectInfo>,
    val tags: List<String>,
    val reviews: List<FilterReview>,
    val similar_filters: List<FilterMetadata>,
    val release_date: Long,
    val last_updated: Long
)

data class FilterRequirements(
    val min_api_level: Int,
    val min_ram_mb: Int,
    val min_storage_mb: Int,
    val supported_devices: List<String>
)

data class FilterRatingRequest(
    val rating: Int,  // 1-5
    val review_text: String? = null
)

data class FilterReviewRequest(
    val rating: Int,
    val title: String,
    val text: String,
    val helpful_count: Int = 0
)

data class FilterUsageReport(
    val filter_id: String,
    val context: String,  // camera, call, edit
    val duration_ms: Long,
    val fps_avg: Float,
    val device_model: String,
    val android_version: Int,
    val timestamp: Long
)

data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int,
    val has_next: Boolean
)
```

### 2.2 Internal Filter Engine API

```kotlin
// FilterEngine.kt - Complete internal API
interface FilterEngine {
    
    // ========== LIFECYCLE ==========
    
    suspend fun initialize()
    suspend fun shutdown()
    fun isInitialized(): Boolean
    
    // ========== FILTER MANAGEMENT ==========
    
    suspend fun loadFilter(filterId: String): Filter
    suspend fun unloadFilter(filterId: String)
    suspend fun switchFilter(filterId: String)
    suspend fun setFilterIntensity(intensity: Float)
    suspend fun applyCustomization(customization: FilterCustomization)
    suspend fun resetCustomization()
    
    // ========== PROCESSING ==========
    
    suspend fun processCameraFrame(frame: VideoFrame): VideoFrame
    suspend fun processCallFrame(frame: VideoFrame, outgoing: Boolean): VideoFrame
    suspend fun applyBeautyEffect(frame: VideoFrame, effect: BeautyEffect): VideoFrame
    suspend fun applyEffect(frame: VideoFrame, effect: Effect): VideoFrame
    
    // ========== FACE TRACKING ==========
    
    fun getFaceData(): List<FaceData>
    fun getFaceCount(): Int
    fun isFaceDetected(): Boolean
    fun getLastTrackingMetrics(): TrackingMetrics?
    
    // ========== QUALITY & PERFORMANCE ==========
    
    fun setRenderingQuality(quality: RenderingQuality)
    fun getRenderingQuality(): RenderingQuality
    fun setTargetFPS(fps: Int)
    fun getTargetFPS(): Int
    fun setAdaptiveQuality(enabled: Boolean)
    
    // ========== STATISTICS & DIAGNOSTICS ==========
    
    fun getPerformanceMetrics(): PerformanceMetrics
    fun getUsageStatistics(): UsageStatistics
    fun getDeviceDiagnostics(): DeviceDiagnostics
    
    // ========== EFFECTS MANIPULATION ==========
    
    fun getActiveEffects(): List<Effect>
    fun addEffect(effect: Effect)
    fun removeEffect(effectId: String)
    fun clearEffects()
    
    // ========== LISTENERS ==========
    
    fun addFilterChangeListener(listener: FilterChangeListener)
    fun removeFilterChangeListener(listener: FilterChangeListener)
    fun addPerformanceListener(listener: PerformanceListener)
    fun removePerformanceListener(listener: PerformanceListener)
}

interface FilterChangeListener {
    fun onFilterChanged(oldFilter: Filter?, newFilter: Filter?)
    fun onEffectAdded(effect: Effect)
    fun onEffectRemoved(effectId: String)
    fun onIntensityChanged(intensity: Float)
}

interface PerformanceListener {
    fun onPerformanceMetricsUpdated(metrics: PerformanceMetrics)
    fun onFPSDrop(currentFPS: Float, targetFPS: Int)
    fun onMemoryWarning(usedMB: Float, maxMB: Float)
}

data class PerformanceMetrics(
    val averageFPS: Float,
    val frameTimeMs: Float,
    val cpuUsage: Float,
    val gpuUsage: Float,
    val memoryUsageMB: Float,
    val faceDetectionTimeMs: Float,
    val renderingTimeMs: Float,
    val thermalStatus: ThermalStatus
)

enum class ThermalStatus {
    NORMAL, MODERATE, CRITICAL, THROTTLING
}

data class UsageStatistics(
    val totalFiltersUsed: Int,
    val mostUsedFilter: String?,
    val totalProcessingTime: Long,
    val averageSessionDuration: Long,
    val contextUsage: Map<String, Int>
)

data class DeviceDiagnostics(
    val deviceModel: String,
    val androidVersion: Int,
    val cpuCores: Int,
    val ramAvailableMB: Float,
    val ramTotalMB: Float,
    val gpuName: String,
    val supportedFormats: List<String>,
    val estimatedQuality: RenderingQuality
)
```

---

## 3. WEBHOOK & EVENT SCHEMAS

### 3.1 Analytics Events

```json
{
  "event_type": "filter_usage",
  "filter_id": "dog_filter_v2",
  "user_id": "user_123",
  "context": "camera",
  "timestamp": 1686000000000,
  "device": {
    "model": "Pixel 6",
    "android_version": 13,
    "ram_mb": 8000
  },
  "metrics": {
    "duration_ms": 5000,
    "fps_avg": 29.5,
    "cpu_usage": 45.2,
    "memory_usage_mb": 256,
    "battery_drain": 1.2
  },
  "outcome": {
    "was_saved": true,
    "was_shared": false,
    "output_path": "file://storage/..."
  }
}
```

---

This comprehensive database and API schema provides:
- Efficient data storage and retrieval
- Support for analytics and recommendations
- Asset management and caching
- User preferences and customization
- Performance monitoring
- Multi-platform compatibility tracking
