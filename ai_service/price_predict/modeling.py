"""Serializable feature builders; requires no additional database fields."""
import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from features import BASE_CATEGORICAL, BASE_NUMERIC

DERIVED_NUMERIC = ["log_area", "area_per_room", "bathrooms_per_room", "high_floor_without_elevator"]
LOCATION_KEYS = ["community_key", "business_area_key"]

class HousingFeatures(TransformerMixin, BaseEstimator):
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        result = X[BASE_NUMERIC + BASE_CATEGORICAL].copy()
        result["log_area"] = np.log1p(result["building_area_sqm"])
        result["area_per_room"] = result["building_area_sqm"] / result["rooms"].replace(0, np.nan)
        result["bathrooms_per_room"] = result["bathrooms"] / result["rooms"].replace(0, np.nan)
        result["high_floor_without_elevator"] = np.where(result["has_elevator"].isna(), np.nan,
            ((result["floor_level"] == "HIGH") & (result["has_elevator"] == 0)).astype(float))
        result["community_key"] = result["district"] + "::" + result["community_name"]
        result["business_area_key"] = result["district"] + "::" + result["business_area"]
        return result
