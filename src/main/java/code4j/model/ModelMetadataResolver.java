package code4j.model;

import java.util.Objects;
import java.util.Optional;

public final class ModelMetadataResolver {
    public ModelContextProfile resolve(String model, Optional<Integer> configuredContextWindow,
                                       Optional<Integer> configuredMaxOutputTokens, Optional<ModelMetadata> metadata) {
        String m = Objects.requireNonNull(model, "model");
        Optional<Integer> ccw = Objects.requireNonNull(configuredContextWindow, "configuredContextWindow");
        Optional<Integer> cmo = Objects.requireNonNull(configuredMaxOutputTokens, "configuredMaxOutputTokens");
        Optional<ModelMetadata> md = Objects.requireNonNull(metadata, "metadata");

        long ctx;
        ModelContextProfile.Source source;
        Optional<Long> pmax = md.flatMap(ModelMetadata::maxInputTokens);
        if (ccw.isPresent()) { ctx = ccw.orElseThrow(); source = ModelContextProfile.Source.RUNTIME_CONFIG; }
        else if (pmax.isPresent()) { ctx = pmax.orElseThrow(); source = ModelContextProfile.Source.PROVIDER_METADATA; }
        else {
            ModelLimits.ContextWindow d = ModelLimits.contextWindow(m);
            ctx = d.contextWindow();
            source = ModelLimits.isKnownContextModel(m) ? ModelContextProfile.Source.LOCAL_MODEL_LIMITS : ModelContextProfile.Source.UNKNOWN_FALLBACK;
        }
        Optional<Integer> pref = md.flatMap(ModelMetadata::maxOutputTokens).isPresent() ? md.flatMap(ModelMetadata::maxOutputTokens) : cmo;
        int resolved = ModelLimits.resolveMaxOutputTokens(m, pref);
        long reserve = Math.min(Math.max(0L, resolved), ctx - 1L);
        return new ModelContextProfile(ctx, reserve, (int) reserve, source, md.flatMap(ModelMetadata::maxOutputTokens));
    }
}
